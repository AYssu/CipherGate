package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.SystemConfig;
import com.ayssu.ciphergate.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "系统配置与初始化相关接口")
public class ConfigController {
    
    private final SystemConfigService systemConfigService;
    private final Environment environment;
    
    @Value("${app.security.init-reset-enabled:false}")
    private boolean initResetEnabled;
    
    @Value("${app.security.init-reset-token:}")
    private String initResetToken;
    
    @GetMapping("/{configKey}")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "查询配置项")
    public Result<Map<String, Object>> getConfig(@PathVariable String configKey) {
        try {
            String value = systemConfigService.getConfigValue(configKey);
            if (value != null) {
                // 如果是敏感信息，只显示部分内容
                if (configKey.contains("secret") || configKey.contains("password")) {
                    value = maskSensitiveValue(value);
                }
                return Result.success(Map.of("configKey", configKey, "configValue", value));
            } else {
                return Result.error("配置项不存在");
            }
        } catch (Exception e) {
            log.error("获取配置失败: {}", e.getMessage());
            return Result.error("获取配置失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{configKey}")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新系统配置项")
    @Operation(summary = "更新配置项")
    public Result<Void> setConfig(
            @PathVariable String configKey,
            @RequestBody Map<String, Object> request) {
        try {
            String configValue = (String) request.get("configValue");
            String description = (String) request.get("description");
            Boolean isEncrypted = (Boolean) request.getOrDefault("isEncrypted", false);
            
            systemConfigService.setConfigValue(configKey, configValue, description, isEncrypted);
            
            return Result.success("配置更新成功", null);
        } catch (Exception e) {
            log.error("设置配置失败: {}", e.getMessage());
            return Result.error("设置配置失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/github/oauth2")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取GitHub OAuth2配置")
    public Result<Map<String, Object>> getGithubOAuth2Config() {
        try {
            String clientId = systemConfigService.getGithubClientId();
            String clientSecret = systemConfigService.getGithubClientSecret();
            
            return Result.success(Map.of(
                "clientId", clientId,
                "clientSecret", maskSensitiveValue(clientSecret)
            ));
        } catch (Exception e) {
            log.error("获取 GitHub OAuth2 配置失败: {}", e.getMessage());
            return Result.error("获取配置失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/refresh")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "刷新系统配置缓存")
    @Operation(summary = "刷新配置缓存")
    public Result<Void> refreshCache() {
        try {
            systemConfigService.refreshCache();
            return Result.success("配置缓存已刷新", null);
        } catch (Exception e) {
            log.error("刷新配置缓存失败: {}", e.getMessage());
            return Result.error("刷新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/database/status")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取数据库配置状态")
    public Result<Map<String, Object>> getDatabaseStatus() {
        try {
            return Result.success(Map.of(
                "message", "数据库状态检查",
                "configService", "正常",
                "githubClientId", systemConfigService.getGithubClientId(),
                "githubClientSecretMasked", maskSensitiveValue(systemConfigService.getGithubClientSecret())
            ));
        } catch (Exception e) {
            log.error("检查数据库状态失败: {}", e.getMessage());
            return Result.error("数据库状态检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查系统是否已初始化（无需权限）
     */
    @GetMapping("/init/status")
    @Operation(summary = "获取系统初始化状态")
    public Result<Map<String, Object>> getInitStatus() {
        try {
            boolean initialized = systemConfigService.isSystemInitialized();
            return Result.success(Map.of(
                "initialized", initialized,
                "allowInit", !initialized
            ));
        } catch (Exception e) {
            log.error("检查初始化状态失败: {}", e.getMessage());
            return Result.error("检查初始化状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化系统配置（无需权限，但只能在默认配置时使用）
     */
    @PostMapping("/init")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "初始化系统配置")
    @Operation(summary = "初始化系统配置")
    public Result<Void> initializeSystem(@RequestBody Map<String, String> request) {
        try {
            if (systemConfigService.isSystemInitialized()) {
                return Result.error("系统已初始化，无法重复配置");
            }
            
            String clientId = request.get("clientId");
            String clientSecret = request.get("clientSecret");
            String redirectUri = request.get("redirectUri");
            String frontendUrl = request.get("frontendUrl");
            
            // 验证参数
            if (clientId == null || clientId.trim().isEmpty()) {
                return Result.error("Client ID 不能为空");
            }
            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                return Result.error("Client Secret 不能为空");
            }
            if (redirectUri == null || redirectUri.trim().isEmpty()) {
                return Result.error("Redirect URI 不能为空");
            }
            if (frontendUrl == null || frontendUrl.trim().isEmpty()) {
                return Result.error("前端地址不能为空");
            }
            
            // 尝试初始化
            boolean success = systemConfigService.initializeSystemConfig(clientId, clientSecret, redirectUri, frontendUrl);
            
            if (success) {
                return Result.success("系统初始化成功", null);
            } else {
                return Result.error("系统已初始化，无法重复配置");
            }
        } catch (Exception e) {
            log.error("初始化系统失败: {}", e.getMessage());
            return Result.error("初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置系统配置（仅用于开发测试）
     */
    @PostMapping("/init/reset")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "重置系统初始化配置")
    @Operation(summary = "重置系统初始化配置")
    public Result<Void> resetSystemConfig(@RequestBody(required = false) Map<String, String> request) {
        try {
            boolean isDevProfile = environment.acceptsProfiles(Profiles.of("dev"));
            if (!isDevProfile && !initResetEnabled) {
                return Result.error("当前环境不允许重置初始化配置");
            }
            
            if (StringUtils.hasText(initResetToken)) {
                String requestToken = request == null ? null : request.get("resetToken");
                if (!initResetToken.equals(requestToken)) {
                    return Result.error("重置令牌无效");
                }
            }
            
            // 重置为默认值
            systemConfigService.setConfigValue("github.oauth2.client-id", "default-client-id", "GitHub OAuth2 Client ID", false);
            systemConfigService.setConfigValue("github.oauth2.client-secret", "default-client-secret", "GitHub OAuth2 Client Secret", true);
            systemConfigService.setConfigValue("github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "GitHub OAuth2 Redirect URI", false);
            systemConfigService.setConfigValue("frontend.url", "http://localhost:5173/dashboard", "前端地址", false);
            systemConfigService.setConfigValue("SYSTEM_INITIALIZED", "false", "系统初始化标记", false);
            systemConfigService.refreshCache();
            
            return Result.success("系统配置已重置", null);
        } catch (Exception e) {
            log.error("重置系统配置失败: {}", e.getMessage());
            return Result.error("重置失败: " + e.getMessage());
        }
    }
    
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}