package com.ayssu.ciphergate.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.ayssu.ciphergate.dto.*;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.impl.ThirdPartyCredentialServiceImpl;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyRechargeDTO;
import com.ayssu.ciphergate.thirdparty.service.impl.ThirdPartyRechargeServiceImpl;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.data.redis.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只接受一次性的独立数据库，真实验证关联查询、事务及充值作用范围。 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CIPHERGATE_CREDENTIAL_JDBC_URL", matches = ".+")
class ThirdPartyCredentialMysqlTest {
    static JdbcTemplate jdbc;
    static SqlSessionTemplate sessions;
    static DataSourceTransactionManager transactions;
    ThirdPartyCredentialServiceImpl service;
    ThirdPartyRechargeServiceImpl recharge;
    SecurityUtils security;
    ValueOperations<String, String> redisValues;

    @BeforeAll static void database() throws Exception {
        String url = System.getenv("CIPHERGATE_CREDENTIAL_JDBC_URL");
        if (!url.contains("/ciphergate_credential_test?")) throw new IllegalArgumentException("仅允许独立测试数据库");
        var ds = new DriverManagerDataSource(url, "root", System.getenv("CIPHERGATE_CREDENTIAL_JDBC_PASSWORD"));
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver"); jdbc = new JdbcTemplate(ds);
        for (String table : List.of("third_party_recharge_log", "third_party_credential_app", "third_party_credential", "app_user", "application", "users"))
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        String init = Files.readString(Path.of("src/main/resources/db/migration/V1__initial_schema.sql"));
        for (String table : List.of("users", "application", "app_user", "third_party_credential", "third_party_credential_app", "third_party_recharge_log")) {
            Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\(.*?;", Pattern.DOTALL).matcher(init);
            assertTrue(m.find(), table); jdbc.execute(m.group());
        }
        // 升级脚本重跑不能扩大授权或删除绑定。
        executeUpgrade(); executeUpgrade();
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(pagination);
        config.setEnvironment(new Environment("credential-test", new SpringManagedTransactionFactory(), ds));
        for (Class<?> mapper : List.of(ApplicationMapper.class, AppUserMapper.class, ThirdPartyCredentialMapper.class,
                ThirdPartyCredentialAppMapper.class, ThirdPartyRechargeLogMapper.class)) config.addMapper(mapper);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        transactions = new DataSourceTransactionManager(ds);
    }
    static void executeUpgrade() throws Exception {
        String sql = Files.readString(Path.of("docs/database/legacy/alter_third_party_credential_apps.sql"))
                .replaceAll("(?m)^--.*$", "");
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        for (String table : List.of("third_party_recharge_log", "third_party_credential_app", "third_party_credential", "app_user", "application", "users")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,github_id,login) VALUES (1,'owner-test','owner-test'),(2,'other-test','other-test')");
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (10,1,'A','app_A_test','secret_test'),(20,1,'B','app_B_test','secret_test'),(30,2,'C','app_C_test','secret_test')");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,password,member_expires_at) VALUES (100,10,'user','user@example.test','test','2030-01-01'),(200,20,'user','user@example.test','test','2030-01-01'),(300,30,'user','user@example.test','test','2030-01-01')");
        jdbc.update("INSERT INTO third_party_credential(id,app_id,name,api_key,api_secret,used_call_count,used_days_count) VALUES (5,10,'legacy','test_key','test_secret',9,63)");
        security = mock(SecurityUtils.class); when(security.isAdmin(99L)).thenReturn(true);
        var target = new ThirdPartyCredentialServiceImpl(sessions.getMapper(ThirdPartyCredentialMapper.class), sessions.getMapper(ThirdPartyCredentialAppMapper.class),
                sessions.getMapper(ThirdPartyRechargeLogMapper.class), sessions.getMapper(ApplicationMapper.class), security);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (ThirdPartyCredentialServiceImpl)proxy.getProxy();
        var redis = mock(StringRedisTemplate.class); redisValues = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisValues.increment(anyString())).thenReturn(1L);
        recharge = new ThirdPartyRechargeServiceImpl(sessions.getMapper(ThirdPartyCredentialMapper.class), sessions.getMapper(ThirdPartyCredentialAppMapper.class),
                sessions.getMapper(ThirdPartyRechargeLogMapper.class), sessions.getMapper(AppUserMapper.class), sessions.getMapper(ApplicationMapper.class),
                redis, mock(ActivityLogService.class), mock(SystemMessageService.class), transactions);
    }
    ThirdPartyCredentialDTO dto(List<Long> ids) {
        var dto = new ThirdPartyCredentialDTO(); dto.setAppIds(ids); dto.setName("multi"); return dto;
    }
    ThirdPartyRechargeDTO request(long appId, String orderNo) {
        var dto = new ThirdPartyRechargeDTO(); dto.setApiKey("test_key"); dto.setUserEmail("user@example.test"); dto.setDays(7);
        dto.setProjectKey(Base64.getEncoder().encodeToString(Long.toString(appId).getBytes(StandardCharsets.UTF_8)));
        dto.setTimestamp(System.currentTimeMillis()); dto.setOutTradeNo(orderNo);
        dto.setSign(DigestUtil.md5Hex("apiKey=test_key&days=7&projectKey=" + dto.getProjectKey() + "&timestamp=" + dto.getTimestamp()
                + "&userEmail=user@example.test&secret=test_secret").toUpperCase(Locale.ROOT));
        return dto;
    }
    int count(long appId, long operator) {
        var query = new ThirdPartyCredentialQueryDTO(); if (appId != 0) query.setAppId(appId);
        return service.pageCredentials(query, operator).getRecords().size();
    }
    LocalDateTime expiry(long id) { return jdbc.queryForObject("SELECT member_expires_at FROM app_user WHERE id=?", LocalDateTime.class, id); }
    String error() { return jdbc.queryForObject("SELECT error_code FROM third_party_recharge_log ORDER BY id DESC LIMIT 1", String.class); }

    @Test void legacyFallbackWorksForAuthorizationAndFiltering() {
        assertEquals(List.of(10L), service.getCredential(5L, 1L).getAppIds());
        assertEquals(1, count(10, 1)); assertEquals(0, count(20, 1));
        assertTrue(sessions.getMapper(ThirdPartyCredentialAppMapper.class).isAppBound(5L, 10L));
        assertFalse(sessions.getMapper(ThirdPartyCredentialAppMapper.class).isAppBound(5L, 20L));
        assertNotEquals(200, recharge.recharge(request(20, "legacy-denied"), "192.0.2.1", "test").getCode());
        assertEquals("APP_DENIED", error());
    }
    @Test void editingBindingsKeepsKeySecretCountersAndClearsNullableConfig() {
        jdbc.update("UPDATE third_party_credential SET allowed_ips='192.0.2.1',daily_limit=10,total_call_limit=100,total_days_limit=1000,expires_at='2030-01-01' WHERE id=5");
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        var row = sessions.getMapper(ThirdPartyCredentialMapper.class).selectById(5L);
        assertEquals("test_key", row.getApiKey()); assertEquals("test_secret", row.getApiSecret());
        assertEquals(9L, row.getUsedCallCount()); assertEquals(63L, row.getUsedDaysCount());
        assertNull(row.getAllowedIps()); assertNull(row.getDailyLimit()); assertNull(row.getTotalCallLimit()); assertNull(row.getTotalDaysLimit()); assertNull(row.getExpiresAt());
        assertEquals(1, count(20, 1)); assertEquals(List.of(10L, 20L), service.pageCredentials(new ThirdPartyCredentialQueryDTO(), 1L).getRecords().get(0).getAppIds());
        assertNull(service.getCredential(5L, 1L).getApiSecret());
    }
    @Test void sameKeyRechargesOnlyRequestedApplicationAndSharesCounters() {
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        assertEquals(200, recharge.recharge(request(20, "app-B-order"), "192.0.2.1", "test").getCode());
        assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(100));
        assertEquals(LocalDateTime.of(2030,1,8,0,0), expiry(200));
        assertEquals(200, recharge.recharge(request(10, "app-A-order"), "192.0.2.1", "test").getCode());
        assertEquals(LocalDateTime.of(2030,1,8,0,0), expiry(100));
        assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(300));
        var row = sessions.getMapper(ThirdPartyCredentialMapper.class).selectById(5L);
        assertEquals(11L, row.getUsedCallCount()); assertEquals(77L, row.getUsedDaysCount());
        verify(redisValues, times(2)).increment("cg:tp:recharge:daily:5:" + LocalDate.now());
    }
    @Test void unboundApplicationStillDeniedEvenWithCorrectSignature() {
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        assertNotEquals(200, recharge.recharge(request(30, "unbound"), "192.0.2.1", "test").getCode());
        assertEquals("APP_DENIED", error()); assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(300));
    }
    @Test void incorrectSignatureRejectedInSecondApplication() {
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        var req = request(20, "bad-sign"); req.setSign("invalid");
        assertNotEquals(200, recharge.recharge(req, "192.0.2.1", "test").getCode());
        assertEquals("BAD_SIGN", error()); assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(200));
    }
    @Test void sameOrderAcrossApplicationsStillOnlyProcessedOnce() {
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        assertEquals(200, recharge.recharge(request(10, "shared-order"), "192.0.2.1", "test").getCode());
        var result = recharge.recharge(request(20, "shared-order"), "192.0.2.1", "test");
        assertEquals(200, result.getCode()); assertEquals(true, ((Map<?,?>) result.getData()).get("alreadyProcessed"));
        assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(200));
        assertEquals(10L, sessions.getMapper(ThirdPartyCredentialMapper.class).selectById(5L).getUsedCallCount());
    }
    @Test void removingApplicationRevokesAuthorizationAndSurvivesUpgradeRerun() throws Exception {
        service.updateCredential(5L, dto(List.of(10L, 20L)), 1L);
        service.updateCredential(5L, dto(List.of(20L)), 1L); executeUpgrade();
        assertEquals(0, count(10, 1)); assertEquals(1, count(20, 1));
        assertNotEquals(200, recharge.recharge(request(10, "revoked"), "192.0.2.1", "test").getCode());
        assertEquals("APP_DENIED", error());
    }
    @Test void ownerCannotListOrManageCrossOwnerSharedKeyEvenWithAppFilter() {
        service.updateCredential(5L, dto(List.of(10L, 30L)), 99L);
        assertEquals(0, count(0, 1)); assertEquals(0, count(10, 1)); assertEquals(0, count(30, 2)); assertEquals(1, count(30, 99));
        assertThrows(RuntimeException.class, () -> service.updateCredential(5L, dto(List.of(10L)), 1L));
        assertThrows(RuntimeException.class, () -> service.rotateSecret(5L, 1L));
        assertThrows(RuntimeException.class, () -> service.deleteCredential(5L, 1L));
    }
    @Test void bindingInsertFailureRollsBackCredentialAndOldBindings() {
        jdbc.execute("CREATE TRIGGER cg_test_binding_fail BEFORE INSERT ON third_party_credential_app FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
        try {
            assertThrows(RuntimeException.class, () -> service.updateCredential(5L, dto(List.of(20L)), 1L));
            assertEquals("legacy", jdbc.queryForObject("SELECT name FROM third_party_credential WHERE id=5", String.class));
            assertEquals(List.of(10L), service.getCredential(5L, 1L).getAppIds());
            assertThrows(RuntimeException.class, () -> service.createCredential(dto(List.of(10L,20L)), 1L));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM third_party_credential", Integer.class));
        } finally { jdbc.execute("DROP TRIGGER cg_test_binding_fail"); }
    }
    @Test void rotationAndDeletionOnlyWriteIntendedColumns() {
        service.updateCredential(5L, dto(List.of(10L,20L)), 1L);
        var rotated = service.rotateSecret(5L, 1L); assertNotEquals("test_secret", rotated.getApiSecret());
        assertEquals(9L, sessions.getMapper(ThirdPartyCredentialMapper.class).selectById(5L).getUsedCallCount());
        service.deleteCredential(5L, 1L);
        assertNull(sessions.getMapper(ThirdPartyCredentialMapper.class).selectById(5L));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM third_party_credential WHERE id=5", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM third_party_credential_app WHERE credential_id=5", Integer.class));
        assertFalse(sessions.getMapper(ThirdPartyCredentialAppMapper.class).isAppBound(5L,10L));
    }
    @Test void totalLimitsAreSharedAcrossBoundApplications() {
        service.updateCredential(5L, dto(List.of(10L,20L)), 1L);
        jdbc.update("UPDATE third_party_credential SET total_call_limit=10 WHERE id=5");
        assertEquals(200, recharge.recharge(request(10,"limit-A"), "192.0.2.1", "test").getCode());
        assertNotEquals(200, recharge.recharge(request(20,"limit-B"), "192.0.2.1", "test").getCode());
        assertEquals("TOTAL_CALL_LIMIT_EXCEEDED", error());
        jdbc.update("UPDATE third_party_credential SET total_call_limit=NULL,total_days_limit=75 WHERE id=5");
        assertNotEquals(200, recharge.recharge(request(20,"days-limit"), "192.0.2.1", "test").getCode());
        assertEquals("TOTAL_DAYS_LIMIT_EXCEEDED", error());
        assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(200));
    }
    @Test void ipExpiryStatusAndDailyLimitsStillApplyToSecondApp() {
        service.updateCredential(5L, dto(List.of(10L,20L)), 1L);
        jdbc.update("UPDATE third_party_credential SET allowed_ips='192.0.2.9' WHERE id=5");
        assertNotEquals(200, recharge.recharge(request(20,"ip"), "192.0.2.1", "test").getCode()); assertEquals("IP_DENIED", error());
        jdbc.update("UPDATE third_party_credential SET allowed_ips=NULL,expires_at='2020-01-01' WHERE id=5");
        assertNotEquals(200, recharge.recharge(request(20,"expiry"), "192.0.2.1", "test").getCode()); assertEquals("CREDENTIAL_EXPIRED", error());
        jdbc.update("UPDATE third_party_credential SET expires_at=NULL,status=0 WHERE id=5");
        assertNotEquals(200, recharge.recharge(request(20,"disabled"), "192.0.2.1", "test").getCode()); assertEquals("BAD_API_KEY", error());
        jdbc.update("UPDATE third_party_credential SET status=1,daily_limit=1 WHERE id=5");
        when(redisValues.increment(anyString())).thenReturn(2L);
        assertNotEquals(200, recharge.recharge(request(20,"daily"), "192.0.2.1", "test").getCode()); assertEquals("DAILY_LIMIT_EXCEEDED", error());
        assertEquals(LocalDateTime.of(2030,1,1,0,0), expiry(200));
    }
    @Test void sharedCredentialLogsRemainScopedToActualApplicationOwner() {
        service.updateCredential(5L, dto(List.of(10L,30L)), 99L);
        assertEquals(200, recharge.recharge(request(10,"log-A"), "192.0.2.1", "test").getCode());
        assertEquals(200, recharge.recharge(request(30,"log-C"), "192.0.2.1", "test").getCode());
        var query = new ThirdPartyRechargeLogQueryDTO(); query.setCredentialId(5L);
        assertEquals(List.of(10L), service.pageRechargeLogs(query,1L).getRecords().stream().map(ThirdPartyRechargeLog::getAppId).toList());
        assertEquals(List.of(30L), service.pageRechargeLogs(query,2L).getRecords().stream().map(ThirdPartyRechargeLog::getAppId).toList());
        assertEquals(2, service.pageRechargeLogs(query,99L).getRecords().size());
        query.setAppId(30L); assertThrows(RuntimeException.class, () -> service.pageRechargeLogs(query,1L));
    }

}
