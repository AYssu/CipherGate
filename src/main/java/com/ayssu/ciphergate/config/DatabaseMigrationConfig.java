package com.ayssu.ciphergate.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** 旧库只能在显式开启接入且通过 V1 结构检查后建立基线。 */
@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfig {
    @Bean
    FlywayMigrationStrategy databaseMigrationStrategy(
            @Value("${app.database.adopt-legacy:false}") boolean adoptLegacy) {
        return flyway -> migrate(flyway, adoptLegacy);
    }

    static void migrate(Flyway flyway, boolean adoptLegacy) {
        // 不允许通过通用配置跳过项目自己的旧库核验。
        if (flyway.getConfiguration().isBaselineOnMigrate()) {
            throw new IllegalStateException("请关闭 spring.flyway.baseline-on-migrate；旧库请使用 CIPHERGATE_ADOPT_LEGACY_DATABASE=true 接入");
        }
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            if (!hasHistory(connection, flyway.getConfiguration().getTable()) && hasTables(connection)) {
                if (!adoptLegacy) {
                    throw new IllegalStateException("检测到尚未接入 Flyway 的已有数据库。请先备份，阅读 docs/database/README.md（部署包 DATABASE-UPGRADE.md），"
                            + "然后仅首次接入设置 CIPHERGATE_ADOPT_LEGACY_DATABASE=true；不会重新执行 init.sql");
                }
                if (!MigrationVersion.fromVersion("1").equals(flyway.getConfiguration().getBaselineVersion())) {
                    throw new IllegalStateException("旧库接入只允许 baseline-version=1，不能跳过后续迁移");
                }
                LegacyDatabaseVerifier.verify(connection);
                flyway.baseline();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("检查数据库迁移状态失败", e);
        }
        // Flyway 负责版本顺序、校验和与迁移锁；错误必须传播，阻止服务继续启动。
        flyway.migrate();
    }

    private static boolean hasHistory(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) > 0;
            }
        }
    }

    private static boolean hasTables(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            rows.next();
            return rows.getInt(1) > 0;
        }
    }
}
