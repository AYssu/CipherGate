package com.ayssu.ciphergate.portal.service;

import com.alibaba.fastjson2.JSON;
import com.ayssu.ciphergate.config.PortalMetaObjectHandler;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.portal.dto.PortalPricingPlanRequest;
import com.ayssu.ciphergate.portal.entity.*;
import com.ayssu.ciphergate.portal.mapper.*;
import com.ayssu.ciphergate.service.SystemConfigService;
import com.ayssu.ciphergate.util.SkPaymentSignature;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.sun.net.httpserver.HttpServer;
import org.apache.ibatis.mapping.Environment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real InnoDB locks, transaction boundaries, gateway failures, and replay/timeout races. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CIPHERGATE_MIGRATION_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalPlanStockMysqlTest {
    JdbcTemplate jdbc;
    SqlSessionTemplate sessions;
    DataSourceTransactionManager transactions;
    PortalPaymentService payments;
    PortalPricingPlanService pricing;
    HttpServer gateway;
    String gatewayUrl;
    AtomicInteger createStatus = new AtomicInteger(200), queryStatus = new AtomicInteger(200), creates = new AtomicInteger();
    AtomicReference<String> remoteState = new AtomicReference<>("CLOSED");
    AtomicReference<Map<String, String>> submitted = new AtomicReference<>();
    static final long APP = 910003, USER = 910002, PLAN = 910004;
    static final String KEY = "stock-test-secret";

    @BeforeAll void database() throws Exception {
        String url = System.getenv("CIPHERGATE_MIGRATION_JDBC_URL");
        if (!url.matches("jdbc:mysql://[^/]+/ciphergate_migration_test(?:\\?.*)?")) throw new IllegalArgumentException("Isolated migration database required");
        var ds = new DriverManagerDataSource(url, System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_USER", "root"), System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : jdbc.queryForList("SHOW TABLES", String.class)) statement.execute("DROP TABLE `" + table.replace("`", "``") + "`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("4").load().migrate();
        jdbc.update("INSERT INTO portal_pricing_plan(id,app_id,plan_name,plan_type,duration_days,price_fen) VALUES(910099,910003,'legacy','MEMBER',30,49900)");
        jdbc.update("INSERT INTO portal_payment_order(order_no,app_user_id,app_id,plan_id,amount_fen,status) VALUES('LEGACY-STOCK',910002,910003,910099,49900,1)");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertEquals(49900L, jdbc.queryForObject("SELECT price_fen FROM portal_pricing_plan WHERE id=910099", Long.class));
        assertNull(jdbc.queryForObject("SELECT stock_remaining FROM portal_pricing_plan WHERE id=910099", Integer.class));
        assertNull(jdbc.queryForObject("SELECT original_price_fen FROM portal_pricing_plan WHERE id=910099", Long.class));
        assertFalse(jdbc.queryForObject("SELECT stock_reserved FROM portal_payment_order WHERE order_no='LEGACY-STOCK'", Boolean.class));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("stock", new SpringManagedTransactionFactory(), ds));
        GlobalConfigUtils.setGlobalConfig(config, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()).setMetaObjectHandler(new PortalMetaObjectHandler()));
        for (Class<?> type : List.of(PortalPricingPlanMapper.class, PortalPaymentOrderMapper.class, ApplicationEpayConfigMapper.class, ApplicationMapper.class, AppUserMapper.class)) config.addMapper(type);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        transactions = new DataSourceTransactionManager(ds);
        var settings = mock(SystemConfigService.class);
        when(settings.getConfigValue(eq("payment.portal.notify.url"), anyString())).thenReturn("https://app.example.test/notify");
        when(settings.getConfigValue(eq("payment.portal.return.url"), anyString())).thenReturn("https://app.example.test/return");
        payments = proxy(new PortalPaymentService(sessions.getMapper(PortalPaymentOrderMapper.class), sessions.getMapper(PortalPricingPlanMapper.class), sessions.getMapper(ApplicationEpayConfigMapper.class), sessions.getMapper(ApplicationMapper.class), sessions.getMapper(AppUserMapper.class), settings));
        pricing = proxy(new PortalPricingPlanService(sessions.getMapper(PortalPricingPlanMapper.class), sessions.getMapper(PortalPaymentOrderMapper.class)));
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/pay/submit", e -> {
            Map<String, String> fields = JSON.parseObject(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
            if (!SkPaymentSignature.verify(fields, KEY, "payment.create", java.time.Instant.now().getEpochSecond())) {
                e.sendResponseHeaders(403, -1); e.close(); return;
            }
            submitted.set(fields); creates.incrementAndGet();
            byte[] body = "{\"code\":200,\"data\":\"https://pay.example.test/submit/test\"}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(createStatus.get(), body.length); e.getResponseBody().write(body); e.close();
        });
        gateway.createContext("/pay/query", e -> {
            Map<String, String> fields = JSON.parseObject(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
            if (!SkPaymentSignature.verify(fields, KEY, "payment.query", java.time.Instant.now().getEpochSecond())) {
                e.sendResponseHeaders(403, -1); e.close(); return;
            }
            byte[] body = JSON.toJSONBytes(Map.of("out_trade_no", fields.get("out_trade_no"), "trade_no", "T1", "type", "alipay", "money", "899.00", "status", remoteState.get()));
            e.sendResponseHeaders(queryStatus.get(), body.length); e.getResponseBody().write(body); e.close();
        });
        gateway.start(); gatewayUrl = "http://127.0.0.1:" + gateway.getAddress().getPort();
        jdbc.update("INSERT IGNORE INTO users(id,github_id,login) VALUES(910001,910001,'stock-test')");
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret,portal_payment_enabled) VALUES(?,910001,'stock-test','stock-test','not-a-real-key',true)", APP);
        jdbc.update("INSERT INTO app_user(id,app_id,username,password) VALUES(?,?,'stock-user','not-a-real-password')", USER, APP);
        jdbc.update("INSERT INTO application_epay_config(app_id,epay_url,epay_pid,epay_key,enabled) VALUES(?,?,'merchant',?,true)", APP, gatewayUrl, KEY);
    }

    @SuppressWarnings("unchecked") <T> T proxy(T target) {
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM portal_payment_order"); jdbc.update("DELETE FROM portal_pricing_plan");
        jdbc.update("UPDATE app_user SET member_expires_at=NULL WHERE id=?", USER);
        jdbc.update("INSERT INTO portal_pricing_plan(id,app_id,plan_name,plan_type,duration_days,price_fen,stock_remaining) VALUES(?,?,'limited','MEMBER',30,89900,1)", PLAN, APP);
        creates.set(0); createStatus.set(200); queryStatus.set(200); remoteState.set("CLOSED");
    }
    @AfterAll void shutdown() { if (gateway != null) gateway.stop(0); }
    int stock() { return jdbc.queryForObject("SELECT stock_remaining FROM portal_pricing_plan WHERE id=?", Integer.class, PLAN); }
    PortalPaymentOrder order() { return payments.createOrder(USER, APP, PLAN); }
    void expire(PortalPaymentOrder order) { jdbc.update("UPDATE portal_payment_order SET created_at=DATE_SUB(NOW(),INTERVAL 31 MINUTE) WHERE id=?", order.getId()); }
    Map<String, String> callback(PortalPaymentOrder order) {
        Map<String, String> fields = new TreeMap<>(Map.of("out_trade_no", order.getOrderNo(), "trade_no", "T1", "trade_status", "TRADE_SUCCESS", "type", "alipay", "pid", "merchant", "money", "899.00", "name", "limited"));
        SkPaymentSignature.seal(fields, KEY, "payment.notify"); return fields;
    }

    @Test void concurrentBuyersCannotOversellThreeUnits() throws Exception {
        jdbc.update("UPDATE portal_pricing_plan SET stock_remaining=3 WHERE id=?", PLAN);
        var pool = Executors.newFixedThreadPool(10); var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 10; i++) attempts.add(pool.submit(() -> { start.await(); try { order(); return true; } catch (IllegalArgumentException soldOut) { return false; } }));
            start.countDown(); int succeeded = 0;
            for (var result : attempts) if (result.get(20, TimeUnit.SECONDS)) succeeded++;
            assertEquals(3, succeeded); assertEquals(0, stock()); assertEquals(3, creates.get());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM portal_payment_order WHERE stock_reserved=true", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void originalPriceNeverChangesCheckoutAndUnlimitedPlansStayUnlimited() {
        jdbc.update("UPDATE portal_pricing_plan SET stock_remaining=NULL,price_fen=2500,original_price_fen=4000 WHERE id=?", PLAN);
        var order = order(); assertEquals(2500L, order.getAmountFen()); assertFalse(order.getStockReserved());
        assertEquals("25.00", submitted.get().get("money"));
        assertTrue(SkPaymentSignature.verify(submitted.get(), KEY, "payment.create", java.time.Instant.now().getEpochSecond()));
        assertNull(jdbc.queryForObject("SELECT stock_remaining FROM portal_pricing_plan WHERE id=?", Integer.class, PLAN));
    }

    @Test void uncertainGatewayFailureCommitsReservationUntilReconciled() {
        createStatus.set(503);
        assertThrows(PortalCheckoutPendingException.class, this::order);
        assertEquals(0, stock()); assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM portal_payment_order WHERE stock_reserved=true AND status=0", Integer.class));
        assertThrows(IllegalArgumentException.class, this::order);
    }

    @ParameterizedTest @ValueSource(strings = {"CLOSED", "FAILED", "REFUNDED", "PENDING", "PAYING"})
    void onlyConfirmedTerminalGatewayStatesReleaseStock(String state) {
        var order = order(); expire(order); remoteState.set(state);
        payments.reconcileExpiredOrder(order.getId()); payments.reconcileExpiredOrder(order.getId());
        boolean closed = Set.of("CLOSED", "FAILED", "REFUNDED").contains(state);
        assertEquals(closed ? 1 : 0, stock());
        assertEquals(closed ? 2 : 0, jdbc.queryForObject("SELECT status FROM portal_payment_order WHERE id=?", Integer.class, order.getId()));
        if (closed) assertFalse(payments.handlePaymentNotify(order.getOrderNo(), "T1", "TRADE_SUCCESS", callback(order)));
    }

    @Test void unavailableOrMissingGatewayCannotReleaseAReservation() {
        var order = order(); expire(order);
        for (int code : List.of(404, 403, 503)) {
            queryStatus.set(code); assertThrows(IllegalStateException.class, () -> payments.reconcileExpiredOrder(order.getId()));
            assertEquals(0, stock()); assertEquals(0, jdbc.queryForObject("SELECT status FROM portal_payment_order WHERE id=?", Integer.class, order.getId()));
        }
    }

    @Test void paidGatewayReconciliationAndConcurrentCallbacksGrantOnlyOnce() throws Exception {
        var order = order(); expire(order); remoteState.set("PAID");
        var fields = callback(order); var pool = Executors.newFixedThreadPool(3); var start = new CountDownLatch(1);
        try {
            var reconcile = pool.submit(() -> { start.await(); payments.reconcileExpiredOrder(order.getId()); return true; });
            var first = pool.submit(() -> { start.await(); return payments.handlePaymentNotify(order.getOrderNo(), "T1", "TRADE_SUCCESS", fields); });
            var retry = pool.submit(() -> { start.await(); return payments.handlePaymentNotify(order.getOrderNo(), "T1", "TRADE_SUCCESS", fields); });
            start.countDown(); assertTrue(first.get(10, TimeUnit.SECONDS)); assertTrue(retry.get(10, TimeUnit.SECONDS)); assertTrue(reconcile.get(10, TimeUnit.SECONDS));
            assertEquals(0, stock()); assertEquals(1, jdbc.queryForObject("SELECT status FROM portal_payment_order WHERE id=?", Integer.class, order.getId()));
            LocalDateTime expiry = jdbc.queryForObject("SELECT member_expires_at FROM app_user WHERE id=?", LocalDateTime.class, USER);
            assertTrue(expiry.isAfter(LocalDateTime.now().plusDays(29))); assertTrue(expiry.isBefore(LocalDateTime.now().plusDays(31)));
            assertFalse(jdbc.queryForObject("SELECT stock_reserved FROM portal_payment_order WHERE id=?", Boolean.class, order.getId()));
        } finally { pool.shutdownNow(); }
    }

    PortalPricingPlanRequest input() {
        var input = new PortalPricingPlanRequest(); input.setPlanName("limited"); input.setPlanType("MEMBER");
        input.setDurationDays(30); input.setPriceFen(89900L); input.setStockRemaining(3); input.setRevision(0L); return input;
    }
    @Test void managementRejectsStaleStockAndCrossApplicationWrites() {
        var input = input(); order();
        assertThrows(IllegalArgumentException.class, () -> pricing.update(APP, PLAN, input));
        assertThrows(IllegalArgumentException.class, () -> pricing.update(APP + 1, PLAN, input));
        assertEquals(0, stock());
        assertThrows(IllegalArgumentException.class, () -> pricing.delete(APP, PLAN));
        input.setRevision(1L); input.setStockRemaining(null);
        assertThrows(IllegalArgumentException.class, () -> pricing.update(APP, PLAN, input));
    }

    @Test void managementClearsDiscountAndSupportsRestockingWithoutChangingOrderSnapshots() {
        var input = input(); input.setOriginalPriceFen(99000L); pricing.update(APP, PLAN, input);
        var order = order(); input.setRevision(2L); input.setOriginalPriceFen(null); input.setPriceFen(99900L); input.setStockRemaining(2);
        pricing.update(APP, PLAN, input); assertEquals(2, stock());
        assertNull(jdbc.queryForObject("SELECT original_price_fen FROM portal_pricing_plan WHERE id=?", Long.class, PLAN));
        assertEquals(89900L, jdbc.queryForObject("SELECT amount_fen FROM portal_payment_order WHERE id=?", Long.class, order.getId()));
    }

    @Test void invalidDiscountAndNegativeStockAreRejected() {
        var input = input(); input.setOriginalPriceFen(100L);
        assertThrows(IllegalArgumentException.class, () -> pricing.create(APP, input));
        input.setOriginalPriceFen(null); input.setStockRemaining(-1);
        assertThrows(IllegalArgumentException.class, () -> pricing.update(APP, PLAN, input));
    }
}
