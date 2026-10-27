package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.impl.*;
import com.ayssu.ciphergate.handler.OrderTimeoutListener;
import com.ayssu.ciphergate.thirdparty.service.UpdateDownloadTrafficService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Destructive test, restricted to the isolated migration database; never application data. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named="CIPHERGATE_MIGRATION_JDBC_URL", matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MembershipConsistencyMysqlTest {
    JdbcTemplate jdbc;
    SqlSessionTemplate sessions;
    DataSourceTransactionManager transactions;
    UserMembershipService members;
    PaymentOrderService orders;
    InviteService invites;
    CheckinService checkins;
    UpdateDownloadTrafficService traffic;
    QuotaProductService products = mock(QuotaProductService.class);
    long sequence = 900000L;
    long user, inviter, otherInviter;

    @BeforeAll void database() {
        String url=System.getenv("CIPHERGATE_MIGRATION_JDBC_URL");
        if(!url.matches("jdbc:mysql://[^/]+/ciphergate_migration_test(?:\\?.*)?")) throw new IllegalArgumentException("Isolated database required");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_USER","root"),System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_PASSWORD",""));
        jdbc=new JdbcTemplate(ds);
        try(var c=ds.getConnection();var statement=c.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for(String table:jdbc.queryForList("SHOW TABLES",String.class)) statement.execute("DROP TABLE `"+table.replace("`","``")+"`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch(Exception e) { throw new IllegalStateException(e); }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("membership-consistency",new SpringManagedTransactionFactory(),ds));
        for(Class<?> mapper:List.of(UserMembershipMapper.class, MembershipLevelMapper.class, BalanceTransactionMapper.class,
                MembershipChangeLogMapper.class, LicenseKeyMapper.class, ApplicationMapper.class, PaymentOrderMapper.class, InviteRecordMapper.class, CheckinRecordMapper.class)) config.addMapper(mapper);
        sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        transactions=new DataSourceTransactionManager(ds);
        var target=new UserMembershipServiceImpl();
        inject(target,"baseMapper",sessions.getMapper(UserMembershipMapper.class));
        inject(target,"userMembershipMapper",sessions.getMapper(UserMembershipMapper.class));
        inject(target,"membershipLevelMapper",sessions.getMapper(MembershipLevelMapper.class));
        inject(target,"balanceTransactionMapper",sessions.getMapper(BalanceTransactionMapper.class));
        inject(target,"changeLogMapper",sessions.getMapper(MembershipChangeLogMapper.class));
        inject(target,"licenseKeyMapper",sessions.getMapper(LicenseKeyMapper.class));
        members=proxy(target);
        traffic=proxy(new UpdateDownloadTrafficService(sessions.getMapper(ApplicationMapper.class),members));
        var orderTarget=new PaymentOrderServiceImpl();
        inject(orderTarget,"baseMapper",sessions.getMapper(PaymentOrderMapper.class));
        inject(orderTarget,"paymentOrderMapper",sessions.getMapper(PaymentOrderMapper.class));
        inject(orderTarget,"quotaProductService",products);inject(orderTarget,"userMembershipService",members);
        inject(orderTarget,"rabbitTemplate",mock(RabbitTemplate.class));orders=proxy(orderTarget);
        var inviteTarget=new InviteServiceImpl();inject(inviteTarget,"baseMapper",sessions.getMapper(InviteRecordMapper.class));
        inject(inviteTarget,"inviteRecordMapper",sessions.getMapper(InviteRecordMapper.class));inject(inviteTarget,"userMembershipService",members);
        var settings=mock(SystemConfigService.class);
        when(settings.getConfigValue("invite.enabled","true")).thenReturn("true");
        when(settings.getConfigValue("invite.reward-amount","300")).thenReturn("300");
        when(settings.getConfigValue("invite.max-count","20")).thenReturn("20");
        inject(inviteTarget,"systemConfigService",settings);invites=proxy(inviteTarget);
        var checkinTarget=new CheckinServiceImpl();inject(checkinTarget,"baseMapper",sessions.getMapper(CheckinRecordMapper.class));
        inject(checkinTarget,"checkinRecordMapper",sessions.getMapper(CheckinRecordMapper.class));inject(checkinTarget,"userMembershipService",members);checkins=proxy(checkinTarget);
    }
    void inject(Object target,String name,Object value){ReflectionTestUtils.setField(target,name,value);}
    @SuppressWarnings("unchecked") <T> T proxy(T target) {
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();
    }
    @BeforeEach void seed(){user=seedUser();inviter=seedUser();otherInviter=seedUser();reset(products);}
    long seedUser(){long id=++sequence;jdbc.update("INSERT INTO users(id,github_id,login) VALUES (?,?,?)",id,"audit-"+id,"audit-"+id);
        jdbc.update("INSERT INTO user_membership(user_id,invite_code,balance) VALUES (?,?,100)",id,"AUDIT-"+id);return id;}
    long balance(long id){return jdbc.queryForObject("SELECT balance FROM user_membership WHERE user_id=?",Long.class,id);}
    int count(String table,long id){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Integer.class,id);}
    List<Boolean> concurrently(Callable<Boolean> a,Callable<Boolean> b)throws Exception {
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var tasks=new ArrayList<Future<Boolean>>();for(var task:List.of(a,b))tasks.add(pool.submit(()->{gate.await();return task.call();}));
            gate.countDown();return List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    @Test void simultaneousCreditsAccumulateAndLedgerBeforeAfterFormsAChain()throws Exception {
        concurrently(()->{members.grantBalance(user,30L,null,"a");return true;},()->{members.grantBalance(user,70L,null,"b");return true;});
        assertEquals(200,balance(user));assertEquals(2,count("balance_transaction",user));
        var rows=jdbc.queryForList("SELECT balance_before,balance_after FROM balance_transaction WHERE user_id=? ORDER BY id",user);
        assertEquals(100,((Number)rows.get(0).get("balance_before")).longValue());
        assertEquals(rows.get(0).get("balance_after"),rows.get(1).get("balance_before"));
        assertEquals(200,((Number)rows.get(1).get("balance_after")).longValue());
    }
    @Test void simultaneousPurchasesCannotSpendOneBalanceTwice()throws Exception {
        var product=new QuotaProduct();product.setId(71L);product.setStatus(1);product.setProductType("LICENSE_QUOTA");
        product.setPrice(80L);product.setQuotaValue(10L);product.setProductName("audit");when(products.getById(71L)).thenReturn(product);
        Callable<Boolean> buy=()->{try{orders.createOrder(user,71L,1);return true;}catch(RuntimeException e){if(!"余额不足".equals(e.getMessage()))throw e;return false;}};
        assertEquals(1,concurrently(buy,buy).stream().filter(Boolean::booleanValue).count());
        assertEquals(20,balance(user));assertEquals(1,count("payment_order",user));
        assertEquals(10,jdbc.queryForObject("SELECT extra_license_quota FROM user_membership WHERE user_id=?",Long.class,user));
    }
    @Test void twoInvitersCannotAwardForTheSameInviteeAndRewardIsNotOverwritten()throws Exception {
        var results=concurrently(()->bind(inviter),()->bind(otherInviter));
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM invite_record WHERE invitee_id=?",Integer.class,user));
        long bound=jdbc.queryForObject("SELECT invited_by FROM user_membership WHERE user_id=?",Long.class,user);
        assertEquals(400,balance(bound));assertEquals(100,balance(bound==inviter?otherInviter:inviter));
        assertEquals(1,jdbc.queryForObject("SELECT invite_count FROM user_membership WHERE user_id=?",Integer.class,bound));
    }
    boolean bind(long id){try{invites.bindInviteCode(user,"AUDIT-"+id);return true;}catch(IllegalStateException e){return false;}}
    @Test void concurrentCheckinsAwardOnlyOnceAndCannotOverwriteCredit()throws Exception {
        concurrently(()->{checkins.doCheckin(user);return true;},()->{members.grantBalance(user,25L,null,"parallel");return true;});
        assertEquals(125,balance(user));assertEquals(1,count("checkin_record",user));
        assertThrows(IllegalStateException.class,()->checkins.doCheckin(user));
    }
    @Test void timeoutAndPaymentCannotLeaveCreditedOrderCancelled()throws Exception {
        String no="audit-order-"+user;
        jdbc.update("INSERT INTO payment_order(order_no,user_id,product_id,product_type,product_name,total_amount,created_at) VALUES (?,?,0,'RECHARGE','audit',50,DATE_SUB(NOW(),INTERVAL 10 MINUTE))",no,user);
        var timeout=new OrderTimeoutListener(orders);
        concurrently(()->{orders.handlePaymentSuccess(no,"audit-trade");return true;},()->{timeout.handleOrderTimeout(Map.of("orderNo",no));return true;});
        int status=jdbc.queryForObject("SELECT status FROM payment_order WHERE order_no=?",Integer.class,no);
        assertTrue(status==1||status==2);assertEquals(status==1?150:100,balance(user));
        assertEquals(status==1?1:0,count("balance_transaction",user));
        if(status==1){timeout.handleOrderTimeout(Map.of("orderNo",no));assertEquals(1,jdbc.queryForObject("SELECT status FROM payment_order WHERE order_no=?",Integer.class,no));}
    }
    @Test void failedLedgerInsertRollsBackBalance() {
        var target=new UserMembershipServiceImpl();inject(target,"baseMapper",sessions.getMapper(UserMembershipMapper.class));
        inject(target,"userMembershipMapper",sessions.getMapper(UserMembershipMapper.class));
        inject(target,"balanceTransactionMapper",mock(BalanceTransactionMapper.class));
        UserMembershipService failing=proxy(target);
        assertThrows(IllegalStateException.class,()->failing.grantBalance(user,25L,null,"failed"));
        assertEquals(100,balance(user));assertEquals(0,count("balance_transaction",user));
    }
    @Test void legacyMembershipInitializationIsIdempotentUnderConcurrency()throws Exception {
        jdbc.update("DELETE FROM user_membership WHERE user_id=?",user);
        concurrently(()->{members.initMembershipForUser(user);return true;},()->{members.initMembershipForUser(user);return true;});
        assertEquals(1,count("user_membership",user));
    }
    @Test void lastLicenseSlotCannotBeConsumedTwiceEvenWithEarlierRepeatableReadSnapshots() throws Exception {
        jdbc.update("UPDATE membership_level SET license_quota=1 WHERE id=(SELECT level_id FROM user_membership WHERE user_id=?)", user);
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (?,?,'audit',?,'test-only')",user,user,"quota-"+user);
        var snapshots = new CountDownLatch(2);
        Callable<Boolean> create = () -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            members.getByUserId(user); // Deliberately create an old REPEATABLE READ snapshot before the owner lock.
            snapshots.countDown();
            try { if (!snapshots.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            if (!members.checkLicenseQuota(user,1)) return false;
            var card=new LicenseKey();card.setAppId(user);card.setOwnerId(user);card.setKeyCode(UUID.randomUUID().toString());card.setKeyType("DAY");
            sessions.getMapper(LicenseKeyMapper.class).insert(card);
            members.consumeLicenseQuota(user,1);
            return true;
        });
        assertEquals(1,concurrently(create,create).stream().filter(Boolean::booleanValue).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM license_key WHERE owner_id=?",Integer.class,user));
    }
    @Test void missingProductRollsBackPaymentClaimInsteadOfMarkingUnfulfilledOrderPaid() {
        String no="missing-product-"+user;
        jdbc.update("INSERT INTO payment_order(order_no,user_id,product_id,product_type,product_name,total_amount) VALUES (?,?,999999,'LICENSE_QUOTA','removed',50)",no,user);
        assertThrows(IllegalStateException.class,()->orders.handlePaymentSuccess(no,"audit-trade"));
        assertEquals(0,jdbc.queryForObject("SELECT status FROM payment_order WHERE order_no=?",Integer.class,no));
        assertEquals(100,balance(user));
        assertEquals(0,count("balance_transaction",user));
    }
    @Test void adminRechargeGrantsExactlyOnceAndLaterGatewayCallbackCannotCreditAgain() {
        String no="admin-recharge-"+user;
        jdbc.update("INSERT INTO payment_order(order_no,user_id,product_id,product_type,product_name,total_amount) VALUES (?,?,0,'RECHARGE','audit',50)",no,user);
        long id=jdbc.queryForObject("SELECT id FROM payment_order WHERE order_no=?",Long.class,no);
        orders.handleAdminGrant(id,inviter);assertEquals(150,balance(user));
        assertThrows(IllegalStateException.class,()->orders.handleAdminGrant(id,inviter));
        orders.handlePaymentSuccess(no,"later-gateway");assertEquals(150,balance(user));
        assertEquals(1,count("balance_transaction",user));
    }

    @Test void simultaneousDownloadsCannotReserveTheLastTrafficAllowanceTwice() throws Exception {
        Application expected=trafficApplication(1000L);
        Callable<Boolean> reserve=()->traffic.reserve(expected,100L);
        assertEquals(1,concurrently(reserve,reserve).stream().filter(Boolean::booleanValue).count());
        assertTrafficUsed(100L,100L);
    }

    @Test void applicationTrafficLimitRejectsWithoutDebitingMembership() {
        Application expected=trafficApplication(99L);
        assertFalse(traffic.reserve(expected,100L));
        assertTrafficUsed(0L,0L);
    }

    @Test void replacedUpdatePackageRejectsStaleDownloadWithoutDebitingTraffic() {
        Application expected=trafficApplication(1000L);
        jdbc.update("UPDATE application SET update_file_storage_key='new-package.bin' WHERE id=?",expected.getId());
        assertFalse(traffic.reserve(expected,100L));
        assertTrafficUsed(0L,0L);
    }

    Application trafficApplication(long appLimit) {
        jdbc.update("UPDATE membership_level SET traffic_quota=100 WHERE id=(SELECT level_id FROM user_membership WHERE user_id=?)",user);
        jdbc.update("UPDATE user_membership SET extra_traffic_quota=0,traffic_used=0 WHERE user_id=?",user);
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret,status,traffic_limit,traffic_used,update_file_storage_key) VALUES (?,?,'audit-traffic',?,'test-only',1,?,0,'package.bin')",user,user,"traffic-"+user,appLimit);
        return sessions.getMapper(ApplicationMapper.class).selectById(user);
    }

    void assertTrafficUsed(long memberBytes,long appBytes) {
        assertEquals(memberBytes,jdbc.queryForObject("SELECT traffic_used FROM user_membership WHERE user_id=?",Long.class,user));
        assertEquals(appBytes,jdbc.queryForObject("SELECT traffic_used FROM application WHERE id=?",Long.class,user));
    }

}
