package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.transfer.*;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsSessionKickService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用一次性的独立 MySQL，覆盖真实唯一键、SQL、事务和提交后回调。未提供测试 DSN 时跳过。 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CIPHERGATE_TRANSFER_JDBC_URL", matches = ".+")
class AppUserTransferMysqlTest {
    static JdbcTemplate jdbc;
    static SqlSessionTemplate sessions;
    static DataSourceTransactionManager transactions;
    AppUserTransferService service;
    AppUserWsSessionKickService kicks;
    UserMembershipService quota;
    SecurityUtils security;

    @BeforeAll static void database() throws Exception {
        String url = System.getenv("CIPHERGATE_TRANSFER_JDBC_URL");
        if (!url.contains("ciphergate_transfer_test")) throw new IllegalArgumentException("测试只允许使用独立 ciphergate_transfer_test 数据库");
        var ds = new DriverManagerDataSource(url, "root", System.getenv("CIPHERGATE_TRANSFER_JDBC_PASSWORD"));
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : List.of("app_user_transfer_batch", "role_menus", "role_permissions", "app_user_binding", "app_user_trial", "app_user", "application", "menus", "permissions", "roles", "users")) jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        String init = Files.readString(Path.of("src/main/resources/db/migration/V1__initial_schema.sql"));
        for (String table : List.of("users", "roles", "permissions", "role_permissions", "menus", "role_menus",
                "application", "app_user", "app_user_binding", "app_user_trial")) {
            Matcher m = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\(.*?;", Pattern.DOTALL).matcher(init);
            assertTrue(m.find(), table);
            jdbc.execute(m.group());
        }
        String batchSql = Files.readString(Path.of("docs/database/legacy/alter_app_user_transfer.sql"));
        Matcher batch = Pattern.compile("CREATE TABLE IF NOT EXISTS app_user_transfer_batch \\(.*?;", Pattern.DOTALL).matcher(batchSql);
        assertTrue(batch.find()); jdbc.execute(batch.group());
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var pagination = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        configuration.addInterceptor(pagination);
        configuration.setEnvironment(new Environment("transfer-test", new SpringManagedTransactionFactory(), ds));
        configuration.addMapper(ApplicationMapper.class);
        configuration.addMapper(AppUserMapper.class);
        configuration.addMapper(AppUserBindingMapper.class);
        configuration.addMapper(AppUserTrialMapper.class);
        configuration.addMapper(AppUserTransferMapper.class);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        transactions = new DataSourceTransactionManager(ds);
    }

    @BeforeEach void setup() {
        for (String t : List.of("app_user_transfer_batch", "app_user_binding", "app_user_trial", "app_user", "application", "users")) jdbc.update("DELETE FROM " + t);
        jdbc.update("INSERT INTO users(id,github_id,login) VALUES (1,'test-owner','test-owner'),(2,'test-other','test-other')");
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret,encryption_config,features) "
                + "VALUES (10,1,'Source','test_source','test_only',JSON_OBJECT('padding','OAEP'),JSON_OBJECT('freeMode',true)),"
                + "(20,1,'Target','test_target','test_only',JSON_OBJECT('padding','PKCS1'),JSON_OBJECT('freeMode',false))");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,password,nickname,member_expires_at,agent_id,login_count) "
                + "VALUES (100,10,'alpha','alpha@example.test','test_encoded_hash','Source profile','2030-01-01',99,25)");
        security = mock(SecurityUtils.class);
        quota = mock(UserMembershipService.class); when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(true);
        kicks = mock(AppUserWsSessionKickService.class);
        var target = new AppUserTransferService(sessions.getMapper(ApplicationMapper.class), sessions.getMapper(AppUserMapper.class),
                sessions.getMapper(AppUserBindingMapper.class), sessions.getMapper(AppUserTrialMapper.class),
                sessions.getMapper(AppUserTransferMapper.class), security, quota, kicks);
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (AppUserTransferService) factory.getProxy();
    }
    AppUserTransferRequest request(boolean move, boolean force) {
        var r = new AppUserTransferRequest(); r.setSourceAppId(10L); r.setTargetAppId(20L);
        r.setMode(move ? AppUserTransferRequest.Mode.MOVE : AppUserTransferRequest.Mode.COPY);
        r.setForceOverwrite(force); r.setUserIds(List.of(100L)); return r;
    }
    void token(AppUserTransferRequest r) { r.setPreviewToken(service.preview(r, 1L).previewToken()); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    void seedFilterUsers() {
        jdbc.update("UPDATE app_user SET phone='13800000001' WHERE id=100");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,nickname,phone,member_expires_at,deleted) VALUES "
                + "(101,10,'beta','beta@example.test','Beta profile','13800000002','2020-01-01',0),"
                + "(102,10,'gamma','gamma@example.test','Gamma profile','13900000003',NULL,0),"
                + "(103,10,'alpha-deleted','alpha-deleted@example.test','Deleted','13800000004',NULL,1),"
                + "(200,20,'alpha-other','alpha-other@example.test','Other','13800000005',NULL,0)");
    }
    @Test void sourceFiltersMatchAllFieldsAndKeepApplicationAndLogicalDeleteScope() {
        seedFilterUsers();
        var f = new AppUserTransferUserQuery(); f.setUsername(" alpha "); f.setNickname("Source");
        f.setEmail("alpha@example"); f.setPhone("00001"); f.setMemberStatus("ACTIVE");
        var page = service.sourceUsers(10L, 1L, 1, 20, "example", f);
        assertEquals(1, page.getTotal()); assertEquals(100L, page.getRecords().get(0).id());
        assertFalse(page.getRecords().toString().contains("test_encoded_hash"));
        f.setNickname("Beta"); assertEquals(0, service.sourceUsers(10L, 1L, 1, 20, null, f).getTotal());
    }
    @Test void sourceKeywordOrDoesNotEscapeApplicationOrLogicalDeleteScope() {
        seedFilterUsers();
        var page = service.sourceUsers(10L, 1L, 1, 20, "alpha");
        assertEquals(1, page.getTotal()); assertEquals(100L, page.getRecords().get(0).id());
    }
    @Test void membershipFiltersAreExclusiveAndPaginationCountsAllMatchingRows() {
        seedFilterUsers();
        for (String status : List.of("ACTIVE", "EXPIRED", "NONE")) {
            var f = new AppUserTransferUserQuery(); f.setMemberStatus(status);
            var page = service.sourceUsers(10L, 1L, 1, 20, null, f);
            assertEquals(1, page.getTotal());
            assertEquals(status.equals("ACTIVE") ? 100L : status.equals("EXPIRED") ? 101L : 102L, page.getRecords().get(0).id());
        }
        var page = service.sourceUsers(10L, 1L, 2, 1, null);
        assertEquals(3, page.getTotal()); assertEquals(101L, page.getRecords().get(0).id());
    }
    @Test void selectedIdsCanBeFilteredWithoutLeakingOtherApplicationOrDeletedUsers() {
        seedFilterUsers(); var f = new AppUserTransferUserQuery();
        f.setUserIds(List.of(100L, 101L, 103L, 200L)); f.setEmail("beta");
        var page = service.sourceUsers(10L, 1L, 1, 20, null, f);
        assertEquals(1, page.getTotal()); assertEquals(101L, page.getRecords().get(0).id());
    }
    @Test void invalidFiltersAreRejectedAndValuesCannotInjectSql() {
        var f = new AppUserTransferUserQuery(); f.setMemberStatus("BAD");
        assertThrows(IllegalArgumentException.class, () -> service.sourceUsers(10L, 1L, 1, 20, null, f));
        f.setMemberStatus(null); f.setUserIds(List.of(0L));
        assertThrows(IllegalArgumentException.class, () -> service.sourceUsers(10L, 1L, 1, 20, null, f));
        f.setUserIds(java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList());
        assertThrows(IllegalArgumentException.class, () -> service.sourceUsers(10L, 1L, 1, 20, null, f));
        f.setUserIds(null); f.setUsername("' OR 1=1 --");
        assertEquals(0, service.sourceUsers(10L, 1L, 1, 20, null, f).getTotal());
        jdbc.update("UPDATE application SET owner_id=2 WHERE id=10");
        assertThrows(SecurityException.class, () -> service.sourceUsers(10L, 1L, 1, 20, null, f));
    }

    @Test void copyWorksWithRealApplicationJsonAndDoesNotTransferHistory() {
        var r = request(false, false); token(r); var result = service.execute(r, 1L);
        assertEquals(1, result.createCount()); assertEquals(2, count("app_user"));
        var copy = jdbc.queryForMap("SELECT * FROM app_user WHERE app_id=20");
        assertEquals("test_encoded_hash", copy.get("password")); assertNull(copy.get("agent_id"));
        assertEquals(0, copy.get("login_count")); assertNull(copy.get("last_login_at"));
        assertFalse(result.toString().contains("test_encoded_hash"));
        verifyNoInteractions(kicks);
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L)); // 重放原快照必须拒绝
        assertEquals(2, count("app_user"));
    }
    @Test void moveSoftDeletesOnlySourceAndKicksAfterActualCommit() {
        var r = request(true, false); token(r); var result = service.execute(r, 1L);
        assertEquals(1, result.createCount());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=100", Integer.class));
        verify(kicks).kickByAppUserId(eq(100L), isNull(), any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE app_id=20 AND deleted=0", Integer.class));
    }
    @Test void overwriteClearsNullableAccountFieldsButKeepsTargetIdAgentAndStats() {
        jdbc.update("UPDATE app_user SET email=NULL,member_expires_at=NULL,nickname=NULL WHERE id=100");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,password,nickname,member_expires_at,agent_id,login_count) "
                + "VALUES (300,20,'alpha','old@example.test','old_hash','Old','2031-01-01',88,9)");
        var r = request(false, true); token(r); var result = service.execute(r, 1L);
        assertEquals(1, result.overwriteCount()); assertEquals(300L, result.items().get(0).targetUserId());
        var dest = jdbc.queryForMap("SELECT * FROM app_user WHERE id=300");
        assertNull(dest.get("email")); assertNull(dest.get("member_expires_at")); assertNull(dest.get("nickname"));
        assertEquals(88L, dest.get("agent_id")); assertEquals(9, dest.get("login_count"));
        verifyNoInteractions(quota); verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
    }
    void seedDeletedTarget() {
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,password,agent_id,login_count,last_login_at,created_at,deleted) "
                + "VALUES(300,20,'ALPHA','old@example.test','old_hash',88,9,'2020-02-01','2020-01-01',1)");
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,device_id,is_banned,status) VALUES(20,300,'ACCOUNT','old-banned-device',1,3)");
        jdbc.update("INSERT INTO app_user_trial(app_id,user_id,trial_started_at,trial_expires_at) VALUES(20,300,'2020-01-01','2020-01-02')");
    }
    @Test void deletedUniqueTargetRestoresInPlaceWithoutForceAndPreservesHistoryRestrictions() {
        seedDeletedTarget();
        jdbc.update("INSERT INTO app_user_trial(app_id,user_id,trial_started_at,trial_expires_at) VALUES(10,100,'2020-01-01','2030-01-01')");
        var r = request(false, false); token(r); var result = service.execute(r, 1L);
        assertEquals(1, result.restoreCount()); assertEquals(0, result.createCount()); assertEquals(0, result.overwriteCount());
        assertEquals(AppUserTransferResult.Action.RESTORE, result.items().get(0).action());
        assertEquals(300L, result.items().get(0).targetUserId()); assertEquals(2, count("app_user"));
        var dest = jdbc.queryForMap("SELECT * FROM app_user WHERE id=300");
        assertEquals(0, dest.get("deleted")); assertEquals("alpha", dest.get("username"));
        assertEquals("alpha@example.test", dest.get("email")); assertEquals("test_encoded_hash", dest.get("password"));
        assertEquals(88L, dest.get("agent_id")); assertEquals(9, dest.get("login_count"));
        assertEquals("2020-01-01 00:00:00", jdbc.queryForObject("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') FROM app_user WHERE id=300", String.class));
        assertEquals("2020-02-01 00:00:00", jdbc.queryForObject("SELECT DATE_FORMAT(last_login_at,'%Y-%m-%d %H:%i:%s') FROM app_user WHERE id=300", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_binding WHERE app_id=20 AND user_id=300 AND is_banned=1 AND deleted=0", Integer.class));
        assertEquals("2020-01-02 00:00:00", jdbc.queryForObject("SELECT DATE_FORMAT(trial_expires_at,'%Y-%m-%d %H:%i:%s') FROM app_user_trial WHERE app_id=20", String.class));
        verify(quota).consumeUserRegisterQuota(1L, 1); verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L)); assertEquals(1, count("app_user_transfer_batch"));
    }
    @Test void moveRestoresDeletedTargetThenDeletesSourceWithoutDoubleQuota() {
        seedDeletedTarget(); when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(false);
        var r = request(true, true); token(r); var result = service.execute(r, 1L);
        assertEquals(1, result.restoreCount());
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=100", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=300", Integer.class));
        verifyNoInteractions(quota); verify(kicks).kickByAppUserId(eq(100L), isNull(), any());
        verify(kicks).kickByAppUserId(eq(300L), isNull(), any());
    }
    @Test void restoringByUniqueEmailReusesTargetIdAndExplicitlyClearsNullableFields() {
        jdbc.update("UPDATE app_user SET nickname=NULL,member_expires_at=NULL,phone=NULL WHERE id=100");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,nickname,member_expires_at,phone,deleted) "
                + "VALUES(300,20,'other-name','ALPHA@example.test','old','2030-01-01','123',1)");
        var r = request(false, false); token(r); service.execute(r, 1L);
        var dest = jdbc.queryForMap("SELECT * FROM app_user WHERE id=300");
        assertEquals("alpha", dest.get("username")); assertNull(dest.get("nickname"));
        assertNull(dest.get("member_expires_at")); assertNull(dest.get("phone")); assertEquals(2, count("app_user"));
    }
    @Test void crossNamespaceDeletedTargetIsStillBlockedWithoutForce() {
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,deleted) VALUES(300,20,'someone','ALPHA',1)");
        var r = request(false, false); token(r); assertEquals(1, service.preview(r, 1L).blockCount());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=300", Integer.class));
    }
    @Test void deletedAndActiveIdentityConflictsMustNotBeMerged() {
        seedDeletedTarget(); jdbc.update("INSERT INTO app_user(id,app_id,username,email) VALUES(301,20,'other','alpha@example.test')");
        var r = request(false, true); token(r); assertEquals(1, service.preview(r, 1L).blockCount());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L)); assertEquals(0, count("app_user_transfer_batch"));
    }
    @Test void deletedStateChangeInvalidatesRestorePreview() {
        seedDeletedTarget(); var r = request(false, false); token(r); jdbc.update("UPDATE app_user SET deleted=0 WHERE id=300");
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L)); assertEquals(0, count("app_user_transfer_batch"));
        assertEquals("old_hash", jdbc.queryForObject("SELECT password FROM app_user WHERE id=300", String.class));
    }
    @Test void failureAfterRestoreRollsBackTargetSourceRestrictionsAndBatch() {
        seedDeletedTarget();
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,device_id) VALUES(10,100,'ACCOUNT','new-device')");
        jdbc.update("UPDATE application SET owner_id=2 WHERE id=20");
        when(security.isAdmin(1L)).thenReturn(true);
        doThrow(new IllegalStateException("test quota write failure")).when(quota).consumeUserRegisterQuota(2L, 1);
        var r = request(true, false); token(r);
        assertThrows(IllegalStateException.class, () -> service.execute(r, 1L));
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=100", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=300", Integer.class));
        assertEquals("old_hash", jdbc.queryForObject("SELECT password FROM app_user WHERE id=300", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_binding WHERE app_id=20 AND device_id='new-device'", Integer.class));
        assertEquals(0, count("app_user_transfer_batch")); verifyNoInteractions(kicks);
    }
    @Test void crossNamespaceConflictsDetectedUsingRealSql() {
        jdbc.update("INSERT INTO app_user(app_id,username,email) VALUES (20,'someone','ALPHA')");
        assertEquals(1, service.preview(request(false, true), 1L).blockCount());
    }
    @Test void usernameAndEmailMatchingDifferentTargetRowsAlwaysBlocked() {
        jdbc.update("INSERT INTO app_user(app_id,username,email) VALUES (20,'alpha','old@example.test'),(20,'other','ALPHA@example.test')");
        var r = request(false, true); token(r);
        assertEquals(1, service.preview(r, 1L).blockCount());
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        assertEquals(3, count("app_user"));
    }
    @Test void copyCarriesBanAndTrialButNotCardEntitlementOrDeviceActivity() {
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,license_key_id,device_id,is_banned,status,last_active_at,expires_at) "
                + "VALUES(10,100,'LICENSE',777,'dev-1',1,3,NOW(),'2031-01-01')");
        jdbc.update("INSERT INTO app_user_trial(app_id,user_id,trial_started_at,trial_expires_at,device_id) "
                + "VALUES(10,100,'2020-01-01','2020-01-02','dev-1')");
        var r = request(false, false); token(r); service.execute(r, 1L);
        var b = jdbc.queryForMap("SELECT * FROM app_user_binding WHERE app_id=20");
        assertEquals("ACCOUNT", b.get("bind_type")); assertNull(b.get("license_key_id")); assertNull(b.get("expires_at"));
        assertNull(b.get("last_active_at")); assertEquals(true, b.get("is_banned"));
        assertEquals(2, count("app_user_trial"));
    }
    @Test void overwriteBanUnionRestoresDeletedDeviceWithoutRemovingTargetBan() {
        jdbc.update("INSERT INTO app_user(app_id,id,username) VALUES(20,300,'alpha')");
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,device_id,is_banned,status,deleted) VALUES "
                + "(10,100,'ACCOUNT','device-a',1,3,0),(10,100,'ACCOUNT','device-b',0,1,0),"
                + "(20,300,'ACCOUNT','DEVICE-A',0,4,1),(20,300,'ACCOUNT','device-b',1,3,0)");
        var r = request(false, true); token(r); service.execute(r, 1L);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_binding WHERE app_id=20 AND is_banned=1 AND deleted=0", Integer.class));
        assertEquals(4, count("app_user_binding"));
    }
    @Test void changingMemberOrDeviceAfterPreviewRejectsWithoutWrites() {
        var r = request(true, false); token(r);
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,device_id) VALUES(10,100,'ACCOUNT','new-device')");
        assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        assertEquals(1, count("app_user")); verifyNoInteractions(kicks);
    }
    @Test void batchIsRolledBackIfADeviceInsertFailsAndNoSessionIsKicked() {
        jdbc.update("INSERT INTO app_user(id,app_id,username) VALUES(101,10,'beta')");
        jdbc.update("INSERT INTO app_user_binding(app_id,user_id,bind_type,device_id) VALUES(10,101,'ACCOUNT','dev-fail')");
        // 只对目标应用注入真实数据库错误，验证事务而不是 mock rollback。
        jdbc.execute("CREATE TRIGGER transfer_test_fail BEFORE INSERT ON app_user_binding FOR EACH ROW "
                + "BEGIN IF NEW.app_id=20 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'; END IF; END");
        try {
            var r = request(true, false); r.setUserIds(List.of(100L, 101L)); token(r);
            assertThrows(RuntimeException.class, () -> service.execute(r, 1L));
            // 第一位用户已插入并软删源账号，第二位写入失败后也必须全部恢复。
            assertEquals(2, count("app_user"));
            assertEquals(0, count("app_user_transfer_batch"));
            assertEquals(0, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=100", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT deleted FROM app_user WHERE id=101", Integer.class));
            verifyNoInteractions(kicks); verify(quota, never()).consumeUserRegisterQuota(anyLong(), anyInt());
        } finally { jdbc.execute("DROP TRIGGER transfer_test_fail"); }
    }
    @Test void identicalOverwriteCannotReuseBatchRequestIdEvenIfSnapshotStillMatches() {
        jdbc.update("INSERT INTO app_user(app_id,id,username,email,password,nickname,member_expires_at) "
                + "VALUES(20,300,'alpha','alpha@example.test','test_encoded_hash','Source profile','2030-01-01')");
        var r = request(false, true); token(r); service.execute(r, 1L);
        // 用新状态生成摘要，但故意重用已成功批次的 UUID。幂等表必须独立于快照保护。
        String newDigest = service.preview(r, 1L).previewToken().split(":")[1];
        r.setPreviewToken(r.getPreviewToken().split(":")[0] + ":" + newDigest);
        var error = assertThrows(IllegalArgumentException.class, () -> service.execute(r, 1L));
        assertTrue(error.getMessage().contains("该批次已执行"));
        assertEquals(1, count("app_user_transfer_batch")); assertEquals(2, count("app_user"));
        verify(kicks, times(1)).kickByAppUserId(eq(300L), isNull(), any());
    }
    @Test void sameOwnerMoveNeedsNoAdditionalQuotaInRealTransaction() {
        when(quota.checkUserRegisterQuota(anyLong(), anyInt())).thenReturn(false);
        var r = request(true, false); token(r); service.execute(r, 1L);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE deleted=0", Integer.class));
        verifyNoInteractions(quota);
    }
    @Test void overwriteKeepsEarlierTargetTrialExpiry() {
        jdbc.update("INSERT INTO app_user(app_id,id,username) VALUES(20,300,'alpha')");
        jdbc.update("INSERT INTO app_user_trial(app_id,user_id,trial_started_at,trial_expires_at) VALUES "
                + "(10,100,'2020-01-01','2030-01-01'),(20,300,'2020-01-01','2021-01-01')");
        var r = request(false, true); token(r); service.execute(r, 1L);
        assertEquals("2021-01-01 00:00:00", jdbc.queryForObject("SELECT DATE_FORMAT(trial_expires_at,'%Y-%m-%d %H:%i:%s') FROM app_user_trial WHERE app_id=20", String.class));
    }
    @Test void migrationSqlCreatesDedicatedMenuAndPermissionIdempotently() throws Exception {
        jdbc.update("DELETE FROM role_menus"); jdbc.update("DELETE FROM role_permissions"); jdbc.update("DELETE FROM menus");
        jdbc.update("DELETE FROM roles"); jdbc.update("DELETE FROM permissions");
        jdbc.update("INSERT INTO roles(role_name,role_code) VALUES ('Test super','SUPER_ADMIN'),('Test admin','ADMIN'),('Test user','USER')");
        jdbc.update("INSERT INTO menus(menu_name,menu_code,menu_type) VALUES ('Applications','APP_MANAGEMENT',1)");
        String sql = Files.readString(Path.of("docs/database/legacy/alter_app_user_transfer.sql"));
        for (int repeat = 0; repeat < 2; repeat++) {
            for (String statement : sql.split(";")) if (!statement.trim().isEmpty()) jdbc.execute(statement);
        }
        assertEquals(2, count("menus")); assertEquals(1, count("permissions"));
        assertEquals(3, count("role_permissions")); assertEquals(3, count("role_menus"));
        assertEquals("/applications/user-transfer", jdbc.queryForObject("SELECT path FROM menus WHERE menu_code='APP_USER_TRANSFER'", String.class));
    }
}
