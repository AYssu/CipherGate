package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.mapper.RawSqlMapper;
import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
            // 读取 SQL 文件
            ClassPathResource resource = new ClassPathResource("sql/init.sql");
            StringBuilder sqlBuilder = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().startsWith("--") && !line.trim().isEmpty()) {
                        sqlBuilder.append(line).append("\n");
                    }
                }
            }
            
            // 分割 SQL 语句并执行
            String[] sqlStatements = sqlBuilder.toString().split(";");
            
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
            
        } catch (Exception e) {
            log.error("初始化默认配置失败: {}", e.getMessage());
        }
    }
}