package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserLicenseRechargeRequest;
import com.ayssu.ciphergate.mapper.*;
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
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual InnoDB row locks, unique keys, and rollback; never connects to an application database. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named="CIPHERGATE_MIGRATION_JDBC_URL", matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppUserLicenseRechargeMysqlTest {
    JdbcTemplate jdbc; SqlSessionTemplate sessions; DataSourceTransactionManager transactions;
    AppUserLicenseRechargeService service; AtomicLong sequence=new AtomicLong(10000);
    long appId,userId,cardId; LocalDateTime before=LocalDateTime.of(2035,1,1,0,0);

    @BeforeAll void database() {
        String url=System.getenv("CIPHERGATE_MIGRATION_JDBC_URL");
        if(!url.matches("jdbc:mysql://[^/]+/ciphergate_migration_test(?:\\?.*)?")) throw new IllegalArgumentException("Isolated migration database required");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_USER","root"),System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_PASSWORD",""));
        jdbc=new JdbcTemplate(ds);
        try(var c=ds.getConnection();var statement=c.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for(String table:jdbc.queryForList("SHOW TABLES",String.class)) statement.execute("DROP TABLE `"+table.replace("`","``")+"`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch(Exception e) { throw new IllegalStateException(e); }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("redemption-test",new SpringManagedTransactionFactory(),ds));
        for(Class<?> mapper:List.of(ApplicationMapper.class,AppUserMapper.class,LicenseKeyMapper.class,AppUserLicenseRechargeMapper.class)) configuration.addMapper(mapper);
        sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        transactions=new DataSourceTransactionManager(ds);
        service=proxied(sessions.getMapper(LicenseKeyMapper.class));
    }
    AppUserLicenseRechargeService proxied(LicenseKeyMapper cards) {
        var target=new AppUserLicenseRechargeService(sessions.getMapper(ApplicationMapper.class),cards,sessions.getMapper(AppUserMapper.class),sessions.getMapper(AppUserLicenseRechargeMapper.class));
        var factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (AppUserLicenseRechargeService)factory.getProxy();
    }
    @BeforeEach void seed() {
        appId=sequence.incrementAndGet(); userId=sequence.incrementAndGet();cardId=sequence.incrementAndGet();
        jdbc.update("INSERT INTO users(id,github_id,login) VALUES (?,?,?)",appId,"redemption-"+appId,"redemption-"+appId);
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (?,?,'Redemption',?, 'test-only')",appId,appId,"redemption-"+appId);
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,member_expires_at) VALUES (?,?,'alice','alice@example.test',?)",userId,appId,before);
        insertCard(cardId,appId);
    }
    void insertCard(long id,long application) {
        jdbc.update("INSERT INTO license_key(id,app_id,owner_id,key_code,key_type,duration_value,duration_unit) VALUES (?,?,?,?,'DAY',7,'DAY')",id,application,appId,"RECHARGE-"+id);
    }
    AppUserLicenseRechargeRequest request(long id) {
        var r=new AppUserLicenseRechargeRequest();r.setAppId(appId);r.setEmail("alice@example.test");r.setKeyCode("RECHARGE-"+id);return r;
    }
    LocalDateTime expiry() { return jdbc.queryForObject("SELECT member_expires_at FROM app_user WHERE id=?",LocalDateTime.class,userId); }
    int ledgerCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM app_user_license_recharge WHERE app_id=?",Integer.class,appId); }

    @Test void successPersistsReceiptAndCannotBeRedeemedAgainEvenAfterStatusReset() {
        service.redeem(request(cardId),"192.0.2.1");assertEquals(before.plusDays(7),expiry()); assertEquals(1,ledgerCount());
        jdbc.update("UPDATE license_key SET status=1,first_used_at=NULL,use_count=0,bind_user_id=NULL,expires_at=NULL WHERE id=?",cardId);
        assertThrows(IllegalArgumentException.class,()->service.redeem(request(cardId),null));
        assertEquals(before.plusDays(7),expiry());assertEquals(1,ledgerCount());
        assertNotNull(jdbc.queryForObject("SELECT redeemed_at FROM license_key WHERE id=?",LocalDateTime.class,cardId));
        assertNull(jdbc.queryForObject("SELECT last_used_at FROM license_key WHERE id=?",LocalDateTime.class,cardId));
        assertFalse(jdbc.queryForObject("SELECT is_online FROM license_key WHERE id=?",Boolean.class,cardId));
    }
    @Test void failedCardConsumptionRollsBackMembershipAndLedger() {
        var cards=spy(sessions.getMapper(LicenseKeyMapper.class));
        doReturn(0).when(cards).update(isNull(),any());
        assertThrows(IllegalStateException.class,()->proxied(cards).redeem(request(cardId),null));
        assertEquals(before,expiry());assertEquals(0,ledgerCount());
        assertNull(jdbc.queryForObject("SELECT redeemed_at FROM license_key WHERE id=?",LocalDateTime.class,cardId));
    }
    @Test void databaseUniqueKeyProtectsAgainstManualStateTampering() {
        service.redeem(request(cardId),null);
        jdbc.update("UPDATE license_key SET status=1,first_used_at=NULL,use_count=0,bind_user_id=NULL,expires_at=NULL,redeemed_at=NULL WHERE id=?",cardId);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->service.redeem(request(cardId),null));
        assertEquals(before.plusDays(7),expiry());assertEquals(1,ledgerCount());
    }
    @Test void anotherApplicationAndDeletedOrActivatedCardsNeverCredit() {
        long otherApp=sequence.incrementAndGet();
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (?,?,'Other',?,'test-only')",otherApp,appId,"other-"+otherApp);
        var wrong=request(cardId);wrong.setAppId(otherApp);assertThrows(IllegalArgumentException.class,()->service.redeem(wrong,null));
        jdbc.update("UPDATE license_key SET deleted=1 WHERE id=?",cardId);assertThrows(IllegalArgumentException.class,()->service.redeem(request(cardId),null));
        jdbc.update("UPDATE license_key SET deleted=0,first_used_at=NOW(),status=2 WHERE id=?",cardId);assertThrows(IllegalArgumentException.class,()->service.redeem(request(cardId),null));
        assertEquals(before,expiry());assertEquals(0,ledgerCount());
    }
    @Test void profileUpdateFromAnOlderSnapshotCannotEraseARecharge() {
        var mapper=spy(sessions.getMapper(AppUserMapper.class));
        var stale=mapper.selectById(userId);
        service.redeem(request(cardId),null);
        doReturn(stale).when(mapper).selectById(userId);
        var profile=new com.ayssu.ciphergate.portal.service.PortalSettingsService(mapper,mock(com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper.class),mock(com.ayssu.ciphergate.portal.service.PortalOtpService.class),mock(PublicAbuseGuard.class));
        profile.updateNickname(userId,"Updated profile");
        assertEquals(before.plusDays(7),expiry());
        assertEquals("Updated profile",jdbc.queryForObject("SELECT nickname FROM app_user WHERE id=?",String.class,userId));
    }
    @Test void simultaneousRedemptionOfOneCardCreditsExactlyOnce() throws Exception {
        List<Boolean> outcomes=concurrently(List.of(cardId,cardId));
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());assertEquals(before.plusDays(7),expiry());assertEquals(1,ledgerCount());
    }
    @Test void differentCardsForOneUserAccumulateInsteadOfOverwriting() throws Exception {
        long second=sequence.incrementAndGet();insertCard(second,appId);
        assertEquals(List.of(true,true),concurrently(List.of(cardId,second)));assertEquals(before.plusDays(14),expiry());assertEquals(2,ledgerCount());
    }
    List<Boolean> concurrently(List<Long> ids) throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> pending=new ArrayList<>();
            for(long id:ids)pending.add(pool.submit(()->{start.await();try{service.redeem(request(id),null);return true;}catch(IllegalArgumentException e){return false;}}));
            start.countDown();List<Boolean> result=new ArrayList<>();for(var f:pending)result.add(f.get(15,TimeUnit.SECONDS));return result;
        } finally {pool.shutdownNow();}
    }
}
