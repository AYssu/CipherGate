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
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
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
    
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}