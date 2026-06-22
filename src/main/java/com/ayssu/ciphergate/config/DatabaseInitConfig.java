package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class DatabaseInitConfig implements CommandLineRunner {

    private final RawSqlMapper rawSqlMapper;
    private final SystemConfigService systemConfigService;

    public DatabaseInitConfig(RawSqlMapper rawSqlMapper, SystemConfigService systemConfigService) {
        this.rawSqlMapper = rawSqlMapper;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        log.info("开始初始化数据库表...");
        
        try {
            // 执行 init.sql（建表/初始化数据）
            executeSqlResource(new ClassPathResource("sql/init.sql"));

            // 执行升级脚本（按顺序执行；需要新增升级时，在这里追加一行即可）
            List<String> upgradeSqlFiles = List.of(
                    "alter_application_unbind_cooldown.sql",
                    "alter_add_password.sql",
                    "alter_spring_session_principal.sql",
                    "alter_membership_system.sql",
                    "alter_membership_extra_quota.sql"
            );
            for (String file : upgradeSqlFiles) {
                try {
                    executeSqlResource(new ClassPathResource("sql/" + file));
                } catch (Exception e) {
                    log.warn("执行升级脚本失败: file={}, err={}", file, e.getMessage());
                }
            }
            
            // 创建 Spring Session 索引（单独处理，避免 IF NOT EXISTS 问题）
            createIndexIfNotExists("SPRING_SESSION_IX1", "CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)");
            createIndexIfNotExists("SPRING_SESSION_IX2", "CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)");
            createIndexIfNotExists("SPRING_SESSION_IX3", "CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)");
            
            // 初始化默认配置（如果不存在）
            initializeDefaultConfigs();
            
            log.info("数据库表初始化完成");
            
        } catch (Exception e) {
            log.error("数据库初始化失败", e);
        }
    }

    private void executeSqlResource(Resource resource) throws Exception {
        StringBuilder sqlBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                    continue;
                }
                sqlBuilder.append(line).append("\n");
            }
        }
        executeSqlStatements(sqlBuilder.toString());
    }

    private void executeSqlStatements(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return;
        }
        String[] sqlStatements = sqlText.split(";");
        for (String sql : sqlStatements) {
            sql = sql.trim();
            if (!sql.isEmpty()) {
                try {
                    rawSqlMapper.executeRawSql(sql);
                    log.debug("执行 SQL 成功: {}", sql.substring(0, Math.min(50, sql.length())) + "...");
                } catch (Exception e) {
                    log.warn("执行 SQL 失败: {}, 错误: {}", sql.substring(0, Math.min(50, sql.length())), e.getMessage());
                }
            }
        }
    }
    
    private void createIndexIfNotExists(String indexName, String createIndexSql) {
        try {
            // 检查索引是否存在
            String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME = '" + indexName + "'";
            Integer count = rawSqlMapper.executeRawQueryForInteger(checkSql);
            
            if (count == null || count == 0) {
                rawSqlMapper.executeRawSql(createIndexSql);
                log.debug("创建索引成功: {}", indexName);
            } else {
                log.debug("索引已存在: {}", indexName);
            }
        } catch (Exception e) {
            log.warn("处理索引 {} 时出错: {}", indexName, e.getMessage());
        }
    }
    
    private void initializeDefaultConfigs() {
        try {
            // 检查并初始化 GitHub OAuth2 配置
            String clientId = systemConfigService.getConfigValue("github.oauth2.client-id");
            if (clientId == null) {
                systemConfigService.setConfigValue("github.oauth2.client-id", "default-client-id", "GitHub OAuth2 Client ID", false);
                log.info("初始化默认 GitHub Client ID");
            }
            
            String clientSecret = systemConfigService.getConfigValue("github.oauth2.client-secret");
            if (clientSecret == null) {
                systemConfigService.setConfigValue("github.oauth2.client-secret", "default-client-secret", "GitHub OAuth2 Client Secret", true);
                log.info("初始化默认 GitHub Client Secret");
            }
            
            String redirectUri = systemConfigService.getConfigValue("github.oauth2.redirect-uri");
            if (redirectUri == null) {
                systemConfigService.setConfigValue("github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "GitHub OAuth2 Redirect URI", false);
                log.info("初始化默认 GitHub Redirect URI");
            }

            // 初始化支付配置（如果不存在）
            initPaymentConfig("payment.epay.url", "https://pay.ayssu.com", "易支付接口地址", false);
            initPaymentConfig("payment.epay.pid", "999999", "易支付商户ID", false);
            initPaymentConfig("payment.epay.key", "62721836Es", "易支付密钥", true);
            initPaymentConfig("payment.epay.notify.url", "", "易支付异步回调地址", false);
            initPaymentConfig("payment.epay.return.url", "", "易支付同步跳转地址", false);
            initPaymentConfig("payment.success.redirect.url", "https://demo.ayssu.com/user/balance", "支付成功跳转地址", false);
            
        } catch (Exception e) {
            log.error("初始化默认配置失败: {}", e.getMessage());
        }
    }

    private void initPaymentConfig(String key, String defaultValue, String description, boolean encrypted) {
        String value = systemConfigService.getConfigValue(key);
        if (value == null) {
            systemConfigService.setConfigValue(key, defaultValue, description, encrypted);
        }
    }
}