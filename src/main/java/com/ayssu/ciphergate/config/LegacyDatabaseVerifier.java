package com.ayssu.ciphergate.config;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 冻结的 V1 接入契约。只读核验，不补字段、不重置业务数据、不恢复已撤销的授权。 */
final class LegacyDatabaseVerifier {
    private static final String PLUGIN_VERSION_INDEX = "I\tplugin_module\tuk_plugin_version";
    private static final String BASELINE_PLUGIN_INDEX = "0\tplugin_id,plugin_version,deleted";
    private static final String LEGACY_PLUGIN_INDEX = "0\tplugin_id,plugin_version,deleted,deleted_at";
    private LegacyDatabaseVerifier() {
    }

    static void verify(Connection connection) throws SQLException {
        Map<String, String> actual = new HashMap<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns "
                     + "WHERE table_schema=DATABASE()")) {
            while (rows.next()) {
                actual.put("C\t" + rows.getString(1) + "\t" + rows.getString(2),
                        normalizeType(rows.getString(3)) + "\t" + rows.getString(4));
            }
        }
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT table_name,index_name,non_unique,"
                     + "GROUP_CONCAT(CONCAT(column_name,IF(collation='D',' DESC',''),"
                     + "IF(sub_part IS NULL,'',CONCAT('(',sub_part,')'))) ORDER BY seq_in_index SEPARATOR ',') "
                     + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                     + "GROUP BY table_name,index_name,non_unique")) {
            while (rows.next()) {
                actual.put("I\t" + rows.getString(1) + "\t" + rows.getString(2),
                        rows.getString(3) + "\t" + rows.getString(4));
            }
        }
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT k.table_name,k.constraint_name,k.referenced_table_name,"
                     + "GROUP_CONCAT(CONCAT(k.column_name,':',k.referenced_column_name) ORDER BY k.ordinal_position SEPARATOR ','),"
                     + "r.update_rule,r.delete_rule FROM information_schema.key_column_usage k "
                     + "JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema "
                     + "AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name "
                     + "WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL "
                     + "GROUP BY k.table_name,k.constraint_name,k.referenced_table_name,r.update_rule,r.delete_rule")) {
            while (rows.next()) {
                actual.put("F\t" + rows.getString(1) + "\t" + rows.getString(2),
                        String.join("\t", rows.getString(3), rows.getString(4), rows.getString(5), rows.getString(6)));
            }
        }
        List<String> differences = new ArrayList<>();
        boolean legacyPluginIndex = false;
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("db/baseline-v1.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", 4);
                if (fields.length != 4) throw new IllegalStateException("损坏的 V1 基线契约");
                String key = fields[0] + "\t" + fields[1] + "\t" + fields[2];
                // One verified historical soft-delete variant, never a general prefix match.
                // Keep the installed index and all rows; V1 and its checksum remain frozen.
                if (PLUGIN_VERSION_INDEX.equals(key) && BASELINE_PLUGIN_INDEX.equals(fields[3])
                        && LEGACY_PLUGIN_INDEX.equals(actual.get(key))) {
                    legacyPluginIndex = true;
                    continue;
                }
                if (!fields[3].equals(actual.get(key))) {
                    differences.add(fields[1] + "." + fields[2] + " 期望=" + fields[3].replace('\t', ' ')
                            + " 实际=" + actual.getOrDefault(key, "缺失").replace('\t', ' '));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 V1 基线契约", e);
        }
        if (legacyPluginIndex) {
            // NULL deleted_at weakens SQL uniqueness for active rows. Refuse ambiguous
            // installed data rather than picking an arbitrary version via LIMIT 1.
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT 1 FROM plugin_module WHERE deleted=0 "
                         + "GROUP BY plugin_id,plugin_version HAVING COUNT(*)>1 LIMIT 1")) {
                if (rows.next()) {
                    differences.add("plugin_module.uk_plugin_version 已知历史索引包含重复的未删除插件版本，需人工核对");
                }
            }
        }
        if (!differences.isEmpty()) {
            throw new IllegalStateException("旧库结构尚不符合 V1，未建立基线、未修改业务数据。请在备份副本中核对并修复，"
                    + "不要绕过检查或手工标记完成。差异 " + differences.size() + " 项：\n"
                    + String.join("\n", differences));
        }
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM system_config WHERE config_key='SYSTEM_INITIALIZED' "
                     + "AND config_value IN ('true','false')")) {
            rows.next();
            if (rows.getInt(1) != 1) {
                throw new IllegalStateException("旧库缺少有效的 SYSTEM_INITIALIZED 标记，拒绝接入；请确认连接的是 CipherGate 数据库");
            }
        }
    }

    // MySQL 8.0 的整数显示宽度与 8.4 不同，不影响存储类型。
    private static String normalizeType(String type) {
        return type.replaceAll("(?i)\\b(tinyint|smallint|mediumint|int|bigint)\\(\\d+\\)", "$1");
    }
}
