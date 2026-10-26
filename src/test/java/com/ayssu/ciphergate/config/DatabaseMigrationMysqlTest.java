package com.ayssu.ciphergate.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 只能连接一次性的 ciphergate_migration_test 数据库；真实覆盖 MySQL DDL 与 Flyway 历史。 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CIPHERGATE_MIGRATION_JDBC_URL", matches = ".+")
class DatabaseMigrationMysqlTest {
    DriverManagerDataSource dataSource;
    JdbcTemplate jdbc;
    @TempDir Path migrations;

    @BeforeEach
    void resetIsolatedDatabase() throws Exception {
        String url = System.getenv("CIPHERGATE_MIGRATION_JDBC_URL");
        if (!url.matches("jdbc:mysql://[^/]+/ciphergate_migration_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("只能使用独立的 ciphergate_migration_test 数据库");
        }
        dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_USER", "root"),
                System.getenv().getOrDefault("CIPHERGATE_MIGRATION_JDBC_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        // 外键开关与 DROP 必须使用同一连接。
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            List<String> tables = jdbc.queryForList("SHOW TABLES", String.class);
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) statement.execute("DROP TABLE `" + table.replace("`", "``") + "`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    Flyway flyway() {
        return Flyway.configure().dataSource(dataSource).baselineVersion("1")
                .locations("classpath:db/migration", "filesystem:" + migrations)
                .validateMigrationNaming(true).cleanDisabled(true).load();
    }

    void legacySchema() throws Exception {
        // 重放归档的真实旧版启动顺序；只允许旧版中已知的重复列/索引错误。
        // 分号切分只用于这些冻结的历史文件，生产 SQL 解析全部由 Flyway 负责。
        var scripts = List.of("init", "alter_application_unbind_cooldown", "alter_add_password",
                "alter_spring_session_principal", "alter_membership_system", "alter_membership_extra_quota",
                "migrate_menus", "alter_portal", "alter_doc_menu_path", "alter_function_plugin_app_access",
                "alter_app_user_transfer", "alter_third_party_credential_apps");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String name : scripts) {
                String sql = Files.readString(Path.of("docs/database/legacy/" + name + ".sql")).lines()
                        .filter(line -> !line.stripLeading().startsWith("--"))
                        .collect(Collectors.joining("\n"));
                for (String part : sql.split(";")) {
                    if (part.isBlank()) continue;
                    try {
                        statement.execute(part);
                    } catch (SQLException e) {
                        if (e.getErrorCode() != 1060 && e.getErrorCode() != 1061) throw e;
                    }
                }
            }
            statement.execute("CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID)");
            statement.execute("CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME)");
            statement.execute("CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME)");
        }
        jdbc.update("UPDATE system_config SET config_value='true' WHERE config_key='SYSTEM_INITIALIZED'");
    }

    int historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
    }

    int historyTableCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'", Integer.class);
    }

    @Test
    void bootAutoConfigurationInitializesEmptyDatabaseBeforeApplicationUseAndRestartPreservesSettings() {
        var contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withUserConfiguration(DatabaseMigrationConfig.class)
                .withBean(DataSource.class, () -> dataSource)
                .withPropertyValues("spring.flyway.baseline-version=1", "spring.flyway.clean-disabled=true");
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(Flyway.class));
            assertEquals(4, historyCount());
            // LegacyDatabaseVerifier deliberately validates only a pre-Flyway V1 database.
            // A freshly migrated database must instead match the latest additive migration.
            assertEquals(254L, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='portal_verify_code' AND column_name='email'", Long.class));
            assertEquals(80L, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='portal_verify_code' AND column_name='purpose'", Long.class));
        });
        jdbc.update("UPDATE menus SET menu_name='自定义名称',sort_order=99 WHERE menu_code='APP_MANAGEMENT'");
        jdbc.update("DELETE FROM role_permissions");
        contextRunner.run(context -> assertNull(context.getStartupFailure()));
        assertEquals(4, historyCount());
        assertEquals("自定义名称", jdbc.queryForObject("SELECT menu_name FROM menus WHERE menu_code='APP_MANAGEMENT'", String.class));
        assertEquals(99, jdbc.queryForObject("SELECT sort_order FROM menus WHERE menu_code='APP_MANAGEMENT'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class));
    }

    @Test
    void v1UpgradePreservesExistingCardAndMembershipAndAppliesRedemptionSchemaOnce() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("1").load().migrate();
        jdbc.update("INSERT INTO users(id,github_id,login) VALUES (9000,'upgrade-owner','upgrade-owner')");
        jdbc.update("INSERT INTO application(id,owner_id,app_name,app_key,app_secret) VALUES (9000,9000,'Upgrade','upgrade-key','test-only')");
        jdbc.update("INSERT INTO app_user(id,app_id,username,email,member_expires_at) VALUES (9000,9000,'existing','existing@example.test','2035-01-01')");
        jdbc.update("INSERT INTO license_key(id,app_id,owner_id,key_code,key_type,duration_value) VALUES (9000,9000,9000,'EXISTINGCARD','DAY',7)");
        var users = jdbc.queryForList("SELECT * FROM app_user WHERE id=9000");
        assertEquals(3, flyway().migrate().migrationsExecuted);
        assertEquals(users, jdbc.queryForList("SELECT * FROM app_user WHERE id=9000"));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM license_key WHERE id=9000", Integer.class));
        assertNull(jdbc.queryForObject("SELECT redeemed_at FROM license_key WHERE id=9000", Object.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_user_license_recharge", Integer.class));
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertEquals(4, historyCount());
    }

    @Test
    void existingDatabaseRequiresExplicitAdoption() throws Exception {
        legacySchema();
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), false));
        assertTrue(error.getMessage().contains("CIPHERGATE_ADOPT_LEGACY_DATABASE"));
        assertEquals(0, historyTableCount());
    }

    @Test
    void adoptionPreservesBusinessDataAndAppliesFutureMigrationOnlyOnce() throws Exception {
        legacySchema();
        jdbc.update("UPDATE menus SET menu_name='保留旧库设置',sort_order=88 WHERE menu_code='APP_MANAGEMENT'");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("INSERT INTO users(github_id,login) VALUES(987654321,'preserved-user')");
        jdbc.update("INSERT INTO third_party_credential(created_by,app_id,name,api_key,api_secret) "
                + "VALUES(1,1,'保留凭证','preserved-key','preserved-secret')");
        Files.writeString(migrations.resolve("V999__test_change.sql"),
                "CREATE TABLE migration_test_marker(id INT PRIMARY KEY); INSERT INTO migration_test_marker VALUES(1);");
        DatabaseMigrationConfig.migrate(flyway(), true);
        assertEquals(5, historyCount());
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history WHERE version='1'", String.class));
        assertEquals("保留旧库设置", jdbc.queryForObject("SELECT menu_name FROM menus WHERE menu_code='APP_MANAGEMENT'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class));
        assertEquals("preserved-user", jdbc.queryForObject("SELECT login FROM users WHERE github_id=987654321", String.class));
        assertEquals("preserved-secret", jdbc.queryForObject("SELECT api_secret FROM third_party_credential WHERE api_key='preserved-key'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT allow_trial FROM third_party_credential WHERE api_key='preserved-key'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT allow_query FROM third_party_credential WHERE api_key='preserved-key'", Integer.class));
        DatabaseMigrationConfig.migrate(flyway(), false);
        assertEquals(5, historyCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM migration_test_marker", Integer.class));
    }

    @Test
    void partialSchemaIsRejectedBeforeAnyHistoryOrBusinessWrite() throws Exception {
        legacySchema();
        jdbc.execute("ALTER TABLE application DROP COLUMN portal_return_url");
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertTrue(error.getMessage().contains("application.portal_return_url"));
        assertEquals(0, historyTableCount());
        assertEquals("true", jdbc.queryForObject("SELECT config_value FROM system_config WHERE config_key='SYSTEM_INITIALIZED'", String.class));
    }

    @Test
    void exactHistoricalPluginIndexIsAdoptedWithoutChangingIndexOrBusinessRows() throws Exception {
        legacySchema();
        replacePluginVersionIndex("UNIQUE", "plugin_id,plugin_version,deleted,deleted_at");
        insertPluginVersion(0, null);
        insertPluginVersion(1, "2025-01-01 00:00:00");
        insertPluginVersion(1, "2025-02-01 00:00:00");
        var preservedRows = jdbc.queryForList("SELECT * FROM plugin_module ORDER BY id");
        jdbc.update("UPDATE menus SET menu_name='保留历史索引接入设置' WHERE menu_code='PLUGIN_MANAGEMENT'");
        DatabaseMigrationConfig.migrate(flyway(), true);
        assertEquals(4, historyCount());
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history WHERE version='1'", String.class));
        assertEquals("0\tplugin_id,plugin_version,deleted,deleted_at", pluginVersionIndex());
        assertEquals(preservedRows, jdbc.queryForList("SELECT * FROM plugin_module ORDER BY id"));
        assertEquals("保留历史索引接入设置", jdbc.queryForObject(
                "SELECT menu_name FROM menus WHERE menu_code='PLUGIN_MANAGEMENT'", String.class));
        DatabaseMigrationConfig.migrate(flyway(), false);
        assertEquals(4, historyCount());
        assertEquals(preservedRows, jdbc.queryForList("SELECT * FROM plugin_module ORDER BY id"));
        assertEquals("0\tplugin_id,plugin_version,deleted,deleted_at", pluginVersionIndex());
    }

    @Test
    void historicalPluginIndexWithDuplicateActiveVersionsIsRejectedWithoutChangingRows() throws Exception {
        legacySchema();
        replacePluginVersionIndex("UNIQUE", "plugin_id,plugin_version,deleted,deleted_at");
        // MySQL permits both because the extra nullable index column is NULL.
        insertPluginVersion(0, null);
        insertPluginVersion(0, null);
        var preservedRows = jdbc.queryForList("SELECT * FROM plugin_module ORDER BY id");
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertTrue(error.getMessage().contains("重复的未删除插件版本"));
        assertEquals(0, historyTableCount());
        assertEquals(preservedRows, jdbc.queryForList("SELECT * FROM plugin_module ORDER BY id"));
        assertEquals("0\tplugin_id,plugin_version,deleted,deleted_at", pluginVersionIndex());
    }

    @Test
    void historicalPluginIndexDoesNotAcceptWrongColumnOrder() throws Exception {
        legacySchema();
        replacePluginVersionIndex("UNIQUE", "plugin_id,plugin_version,deleted_at,deleted");
        assertPluginVersionIndexRejected();
    }

    @Test
    void historicalPluginIndexDoesNotAcceptNonUniqueDefinition() throws Exception {
        legacySchema();
        replacePluginVersionIndex("", "plugin_id,plugin_version,deleted,deleted_at");
        assertPluginVersionIndexRejected();
    }

    @Test
    void historicalPluginIndexDoesNotAcceptOtherExtraColumns() throws Exception {
        legacySchema();
        replacePluginVersionIndex("UNIQUE", "plugin_id,plugin_version,deleted,status");
        assertPluginVersionIndexRejected();
    }

    @Test
    void historicalPluginIndexExceptionDoesNotPermitChangesToAnyOtherIndex() throws Exception {
        legacySchema();
        replacePluginVersionIndex("UNIQUE", "plugin_id,plugin_version,deleted,deleted_at");
        jdbc.execute("ALTER TABLE plugin_module DROP INDEX idx_status, ADD INDEX idx_status(status,deleted_at)");
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertTrue(error.getMessage().contains("plugin_module.idx_status"));
        assertEquals(0, historyTableCount());
        assertEquals("0\tplugin_id,plugin_version,deleted,deleted_at", pluginVersionIndex());
    }

    private void replacePluginVersionIndex(String uniqueness, String columns) {
        jdbc.execute("ALTER TABLE plugin_module DROP INDEX uk_plugin_version, ADD " + uniqueness
                + " INDEX uk_plugin_version(" + columns + ")");
    }

    private void insertPluginVersion(int deleted, String deletedAt) {
        jdbc.update("INSERT INTO plugin_module(plugin_id,plugin_version,plugin_name,bucket_name,object_key,sha256,deleted,deleted_at) "
                + "VALUES('preserved-plugin','1.0.0','保留插件','original-bucket','original-key',?,?,?)",
                "a".repeat(64), deleted, deletedAt);
    }

    private String pluginVersionIndex() {
        return jdbc.queryForObject("SELECT CONCAT(non_unique,CHAR(9),GROUP_CONCAT(column_name ORDER BY seq_in_index)) "
                + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                + "AND table_name='plugin_module' AND index_name='uk_plugin_version' GROUP BY non_unique", String.class);
    }

    private void assertPluginVersionIndexRejected() {
        var originalIndex = pluginVersionIndex();
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertTrue(error.getMessage().contains("plugin_module.uk_plugin_version"));
        assertEquals(0, historyTableCount());
        assertEquals(originalIndex, pluginVersionIndex());
    }

    @Test
    void wrongColumnTypeAndWrongIndexDefinitionAreRejected() throws Exception {
        legacySchema();
        jdbc.execute("ALTER TABLE users MODIFY COLUMN password VARCHAR(32) NULL");
        jdbc.execute("DROP INDEX idx_application_page ON application");
        jdbc.execute("CREATE INDEX idx_application_page ON application(deleted,id)");
        var error = assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertTrue(error.getMessage().contains("users.password"));
        assertTrue(error.getMessage().contains("application.idx_application_page"));
        assertEquals(0, historyTableCount());
    }

    @Test
    void unrelatedDatabaseAndBaselineBypassAreRejected() {
        jdbc.execute("CREATE TABLE unrelated(id INT)");
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(flyway(), true));
        assertEquals(0, historyTableCount());
        Flyway bypass = Flyway.configure().dataSource(dataSource).baselineOnMigrate(true).load();
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationConfig.migrate(bypass, true));
        assertEquals(0, historyTableCount());
    }

    @Test
    void editedAppliedMigrationFailsValidation() throws Exception {
        Files.writeString(migrations.resolve("V999__test_change.sql"), "CREATE TABLE migration_test_marker(id INT);");
        DatabaseMigrationConfig.migrate(flyway(), false);
        Files.writeString(migrations.resolve("V999__test_change.sql"), "CREATE TABLE migration_test_marker(id BIGINT);");
        assertThrows(RuntimeException.class, () -> DatabaseMigrationConfig.migrate(flyway(), false));
        assertEquals(5, historyCount());
    }

    @Test
    void failedMigrationStopsLaterVersions() throws Exception {
        DatabaseMigrationConfig.migrate(flyway(), false);
        Files.writeString(migrations.resolve("V999__failure.sql"), "INSERT INTO missing_table VALUES(1);");
        Files.writeString(migrations.resolve("V1000__must_not_run.sql"), "CREATE TABLE migration_test_marker(id INT);");
        assertThrows(RuntimeException.class, () -> DatabaseMigrationConfig.migrate(flyway(), false));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='migration_test_marker'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='999'", Integer.class));
    }
}
