package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** 补齐分页索引。只尝试在线 DDL，失败保留明确日志，不回退到锁表建索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryIndexSchemaInitializer {
    private final RawSqlMapper rawSqlMapper;

    private record Index(String table, String name, String columns) {
    }

    private static final List<Index> INDEXES = List.of(
            new Index("application", "idx_application_page", "deleted, created_at, id"),
            new Index("application", "idx_application_owner_page", "owner_id, deleted, created_at, id"),
            new Index("license_key", "idx_license_page", "deleted, created_at, id"),
            new Index("license_key", "idx_license_app_page", "app_id, deleted, created_at, id"),
            new Index("app_user", "idx_app_user_page", "deleted, created_at, id"),
            new Index("app_user", "idx_app_user_app_page", "app_id, deleted, created_at, id"),
            new Index("app_user_binding", "idx_binding_user_page", "user_id, deleted, created_at, id"),
            new Index("app_user_binding", "idx_binding_user_banned", "user_id, deleted, is_banned"),
            new Index("app_variable", "idx_variable_page", "deleted, sort_order ASC, created_at DESC, id DESC"),
            new Index("app_variable", "idx_variable_app_page", "app_id, deleted, sort_order ASC, created_at DESC, id DESC"),
            new Index("app_variable_history", "idx_variable_history_page", "variable_id, operated_at, id")
    );

    public void initialize() {
        for (Index index : INDEXES) {
            try {
                Integer count = rawSqlMapper.executeRawQueryForInteger(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + index.table()
                                + "' AND INDEX_NAME = '" + index.name() + "'");
                if (count != null && count > 0) {
                    continue;
                }
                // 名称及列名只来自固定列表，无用户输入。
                rawSqlMapper.executeRawSql("CREATE INDEX " + index.name() + " ON " + index.table()
                        + " (" + index.columns() + ") ALGORITHM=INPLACE LOCK=NONE");
                log.info("已补齐分页索引: table={}, index={}", index.table(), index.name());
            } catch (Exception e) {
                log.warn("在线补齐分页索引失败，请检查数据库版本/DDL权限并安排维护: table={}, index={}, error={}",
                        index.table(), index.name(), e.getMessage());
            }
        }
    }
}
