package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 为既有安装补齐登录统计字段；每次启动检查，可重复执行且不修改历史数据。 */
@Component
@RequiredArgsConstructor
public class AccessEventSchemaInitializer {
    private final RawSqlMapper rawSqlMapper;

    public void initialize() {
        ensureColumn("device_hash", "VARCHAR(64) NULL COMMENT '应用隔离的设备摘要，非认证凭证'");
        ensureColumn("client_ip", "VARCHAR(64) NULL COMMENT '本次登录来源IP'");
        Integer count = rawSqlMapper.executeRawQueryForInteger("SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'access_event' "
                + "AND INDEX_NAME = 'idx_access_login_stats'");
        if (count == null || count == 0) {
            rawSqlMapper.executeRawSql("CREATE INDEX idx_access_login_stats "
                    + "ON access_event (app_id, event_type, created_at, ref_id, device_hash)");
        }
    }

    private void ensureColumn(String name, String definition) {
        Integer count = rawSqlMapper.executeRawQueryForInteger("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'access_event' AND COLUMN_NAME = '" + name + "'");
        if (count == null || count == 0) {
            // name / definition 只来自上面的固定常量，不接受外部输入。
            rawSqlMapper.executeRawSql("ALTER TABLE access_event ADD COLUMN " + name + " " + definition);
        }
    }
}
