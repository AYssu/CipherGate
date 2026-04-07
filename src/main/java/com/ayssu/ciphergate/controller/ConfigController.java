package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.entity.SystemConfig;
import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {
    
    private final SystemConfigService systemConfigService;
    
    @GetMapping("/{configKey}")
    @RequirePermission("CONFIG_LIST")
    public Map<String, Object> getConfig(@PathVariable String configKey) {
        try {
            String value = systemConfigService.getConfigValue(configKey);
            if (value != null) {
                // 如果是敏感信息，只显示部分内容
                if (configKey.contains("secret") || configKey.contains("password")) {
                    value = maskSensitiveValue(value);
                }
                return Map.of(
                    "success", true,
                    "configKey", configKey,
                    "configValue", value
                );
            } else {
                return Map.of(
                    "success", false,
                    "message", "配置项不存在"
                );
            }
        } catch (Exception e) {
            log.error("获取配置失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "获取配置失败: " + e.getMessage()
            );
        }
    }
    
    @PostMapping("/{configKey}")
    @RequirePermission("CONFIG_UPDATE")
    public Map<String, Object> setConfig(
            @PathVariable String configKey,
            @RequestBody Map<String, Object> request) {
        try {
            String configValue = (String) request.get("configValue");
            String description = (String) request.get("description");
            Boolean isEncrypted = (Boolean) request.getOrDefault("isEncrypted", false);
            
            systemConfigService.setConfigValue(configKey, configValue, description, isEncrypted);
            
            return Map.of(
                "success", true,
                "message", "配置更新成功"
            );
        } catch (Exception e) {
            log.error("设置配置失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "设置配置失败: " + e.getMessage()
            );
        }
    }
    
    @GetMapping("/github/oauth2")
    @RequirePermission("CONFIG_LIST")
    public Map<String, Object> getGithubOAuth2Config() {
        try {
            String clientId = systemConfigService.getGithubClientId();
            String clientSecret = systemConfigService.getGithubClientSecret();
            
            return Map.of(
                "success", true,
                "clientId", clientId,
                "clientSecret", maskSensitiveValue(clientSecret)
            );
        } catch (Exception e) {
            log.error("获取 GitHub OAuth2 配置失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "获取配置失败: " + e.getMessage()
            );
        }
    }
    
    @PostMapping("/refresh")
    @RequirePermission("CONFIG_UPDATE")
    public Map<String, Object> refreshCache() {
        try {
            systemConfigService.refreshCache();
            return Map.of(
                "success", true,
                "message", "配置缓存已刷新"
            );
        } catch (Exception e) {
            log.error("刷新配置缓存失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "刷新失败: " + e.getMessage()
            );
        }
    }
    
    @GetMapping("/database/status")
    @RequirePermission("CONFIG_LIST")
    public Map<String, Object> getDatabaseStatus() {
        try {
            return Map.of(
                "success", true,
                "message", "数据库状态检查",
                "configService", "正常",
                "githubClientId", systemConfigService.getGithubClientId(),
                "githubClientSecretMasked", maskSensitiveValue(systemConfigService.getGithubClientSecret())
            );
        } catch (Exception e) {
            log.error("检查数据库状态失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "数据库状态检查失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 检查系统是否已初始化（无需权限）
     */
    @GetMapping("/init/status")
    public Map<String, Object> getInitStatus() {
        try {
            boolean initialized = systemConfigService.isSystemInitialized();
            return Map.of(
                "success", true,
                "initialized", !initialized
            );
        } catch (Exception e) {
            log.error("检查初始化状态失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "检查初始化状态失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 初始化系统配置（无需权限，但只能在默认配置时使用）
     */
    @PostMapping("/init")
    public Map<String, Object> initializeSystem(@RequestBody Map<String, String> request) {
        try {
            String clientId = request.get("clientId");
            String clientSecret = request.get("clientSecret");
            String redirectUri = request.get("redirectUri");
            String frontendUrl = request.get("frontendUrl");
            
            // 验证参数
            if (clientId == null || clientId.trim().isEmpty()) {
                return Map.of("success", false, "message", "Client ID 不能为空");
            }
            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                return Map.of("success", false, "message", "Client Secret 不能为空");
            }
            if (redirectUri == null || redirectUri.trim().isEmpty()) {
                return Map.of("success", false, "message", "Redirect URI 不能为空");
            }
            if (frontendUrl == null || frontendUrl.trim().isEmpty()) {
                return Map.of("success", false, "message", "前端地址不能为空");
            }
            
            // 尝试初始化
            boolean success = systemConfigService.initializeSystemConfig(clientId, clientSecret, redirectUri, frontendUrl);
            
            if (success) {
                return Map.of(
                    "success", true,
                    "message", "系统初始化成功"
                );
            } else {
                return Map.of(
                    "success", false,
                    "message", "系统已初始化，无法重复配置"
                );
            }
        } catch (Exception e) {
            log.error("初始化系统失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "初始化失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 重置系统配置（仅用于开发测试）
     */
    @PostMapping("/init/reset")
    public Map<String, Object> resetSystemConfig() {
        try {
            // 重置为默认值
            systemConfigService.setConfigValue("github.oauth2.client-id", "default-client-id", "GitHub OAuth2 Client ID", false);
            systemConfigService.setConfigValue("github.oauth2.client-secret", "default-client-secret", "GitHub OAuth2 Client Secret", true);
            systemConfigService.setConfigValue("github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "GitHub OAuth2 Redirect URI", false);
            systemConfigService.setConfigValue("frontend.url", "http://localhost:5173/dashboard", "前端地址", false);
            systemConfigService.setConfigValue("SYSTEM_INITIALIZED", "false", "系统初始化标记", false);
            systemConfigService.refreshCache();
            
            return Map.of(
                "success", true,
                "message", "系统配置已重置"
            );
        } catch (Exception e) {
            log.error("重置系统配置失败: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "重置失败: " + e.getMessage()
            );
        }
    }
    
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}