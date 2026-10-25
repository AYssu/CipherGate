package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.*;
import org.apache.ibatis.mapping.Environment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real InnoDB serialization and rollback, confined to the dedicated CI database. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named="CIPHERGATE_MIGRATION_JDBC_URL",matches=".+")
class PortalOtpMysqlTest {
    JdbcTemplate jdbc;TransactionTemplate transaction;PortalOtpService otp;SqlSessionTemplate sessions;
    @BeforeEach void database() {
        String url=System.getenv("CIPHERGATE_MIGRATION_JDBC_URL");
        if(!url.matches("jdbc:mysql://[^/]+/ciphergate_migration_test(?:\\?.*)?"))throw new IllegalArgumentException("Isolated migration database required");
        var ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_USER","root"),System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_PASSWORD",""));
        jdbc=new JdbcTemplate(ds);
        // Other migration cases deliberately leave failed future versions. Reset only this guarded disposable DB.
        try (var connection=ds.getConnection(); var statement=connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for(String table:jdbc.queryForList("SHOW TABLES",String.class))
                statement.execute("DROP TABLE `"+table.replace("`","``")+"`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch(java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("otp",new SpringManagedTransactionFactory(),ds));config.addMapper(PortalVerifyCodeMapper.class);config.addMapper(com.ayssu.ciphergate.mapper.AppUserMapper.class);
        sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        otp=new PortalOtpService(sessions.getMapper(PortalVerifyCodeMapper.class),mock(StringRedisTemplate.class),mock(PublicAbuseGuard.class),mock(SystemSmtpMailService.class));
        jdbc.update("DELETE FROM portal_verify_code WHERE email='otp-test@example.test'");
        jdbc.update("INSERT INTO portal_verify_code(email,code,purpose,used,expires_at)VALUES('otp-test@example.test','123456','RECOVERY',false,?)",LocalDateTime.now().plusMinutes(5));
    }
    @Test void twoConcurrentConsumersOnlyOneSucceeds() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> consume=()->{start.await();try{transaction.executeWithoutResult(status->otp.verify("otp-test@example.test","RECOVERY","123456",true));return true;}catch(IllegalArgumentException expected){return false;}};
        try {var first=pool.submit(consume);var second=pool.submit(consume);start.countDown();assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
    }
    @Test void downstreamFailureRollsBackConsumption() {
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{otp.verify("otp-test@example.test","RECOVERY","123456",true);throw new IllegalStateException("credential write failed");}));
        assertFalse(jdbc.queryForObject("SELECT used FROM portal_verify_code WHERE email='otp-test@example.test'",Boolean.class));
        transaction.executeWithoutResult(status->otp.verify("otp-test@example.test","RECOVERY","123456",true));
        assertTrue(jdbc.queryForObject("SELECT used FROM portal_verify_code WHERE email='otp-test@example.test'",Boolean.class));
    }
    @Test void oldEmailRecoveryWaitsForConcurrentRenameAndCannotResetRenamedAccount() throws Exception {
        long owner=910001,userId=910002;
        jdbc.update("INSERT IGNORE INTO users(id,github_id,login) VALUES (?, 'otp-race-owner', 'otp-race-owner')",owner);
        jdbc.update("INSERT IGNORE INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (?,?,'OTP race','otp-race','test-only')",owner,owner);
        jdbc.update("DELETE FROM app_user WHERE id=?",userId);
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,password) VALUES (?,?,'otp-race','otp-test@example.test','original-password-hash')",userId,owner);
        var users=sessions.getMapper(com.ayssu.ciphergate.mapper.AppUserMapper.class);
        var auth=new PortalAuthService(users,mock(com.ayssu.ciphergate.mapper.ApplicationMapper.class),mock(com.ayssu.ciphergate.portal.mapper.PortalLoginLogMapper.class),
            sessions.getMapper(PortalVerifyCodeMapper.class),new com.ayssu.ciphergate.portal.util.PortalJwtUtil("otp-race-test-secret-long-enough-123456789",60000),mock(StringRedisTemplate.class),
            mock(com.ayssu.ciphergate.service.GeoIpService.class),mock(SystemSmtpMailService.class),mock(PublicAbuseGuard.class),otp,mock(PortalRecoveryDispatcher.class));
        var request=new com.ayssu.ciphergate.portal.dto.PortalPasswordRecoveryRequest();request.setAppId(owner);request.setEmail("otp-test@example.test");request.setVerifyCode("123456");request.setNewPassword("new-password");
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var resetStarted=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var rename=pool.submit(()->transaction.executeWithoutResult(status->{users.selectForUpdate(userId);jdbc.update("UPDATE app_user SET email='renamed@example.test' WHERE id=?",userId);locked.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            var reset=pool.submit(()->{resetStarted.countDown();assertThrows(IllegalArgumentException.class,()->transaction.executeWithoutResult(status->auth.resetPassword(request,"192.0.2.1")));});
            assertTrue(resetStarted.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->reset.get(200,TimeUnit.MILLISECONDS));release.countDown();rename.get(10,TimeUnit.SECONDS);reset.get(10,TimeUnit.SECONDS);
            assertEquals("original-password-hash",jdbc.queryForObject("SELECT password FROM app_user WHERE id=?",String.class,userId));
            assertFalse(jdbc.queryForObject("SELECT used FROM portal_verify_code WHERE email='otp-test@example.test'",Boolean.class));
        } finally {release.countDown();pool.shutdownNow();}
    }

}
