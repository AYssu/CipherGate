package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.SystemConfigService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "系统配置与初始化相关接口")
public class ConfigController {
    
    private final SystemConfigService systemConfigService;
    private final Environment environment;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    
    @Value("${app.security.init-reset-enabled:false}")
    private boolean initResetEnabled;
    
    @Value("${app.security.init-reset-token:}")
    private String initResetToken;

    @Value("${app.site.icp-record-no:}")
    private String icpRecordNo;

    @Value("${app.site.public-security-record-no:}")
    private String publicSecurityRecordNo;

    @Value("${app.site.icp-license-no:}")
    private String icpLicenseNo;
    
    @GetMapping("/{configKey}")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "查询配置项")
    public Result<Map<String, Object>> getConfig(@PathVariable String configKey) {
        try {
            requireSuperAdmin();
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
            requireSuperAdmin();
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
            requireSuperAdmin();
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
            requireSuperAdmin();
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
            requireSuperAdmin();
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

    @GetMapping("/public/site-info")
    @Operation(summary = "获取站点公共展示信息")
    public Result<Map<String, Object>> getPublicSiteInfo() {
        String siteIcpRecordNo = systemConfigService.getConfigValue("site.icp-record-no", icpRecordNo);
        String sitePublicSecurityRecordNo = systemConfigService.getConfigValue("site.public-security-record-no", publicSecurityRecordNo);
        String siteIcpLicenseNo = systemConfigService.getConfigValue("site.icp-license-no", icpLicenseNo);
        return Result.success(Map.of(
                "icpRecordNo", siteIcpRecordNo == null ? "" : siteIcpRecordNo,
                "publicSecurityRecordNo", sitePublicSecurityRecordNo == null ? "" : sitePublicSecurityRecordNo,
                "icpLicenseNo", siteIcpLicenseNo == null ? "" : siteIcpLicenseNo
        ));
    }

    @GetMapping("/settings")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取系统配置中心信息")
    public Result<Map<String, Object>> getSettings() {
        try {
            requireSuperAdmin();
            Map<String, Object> data = new HashMap<>();
            data.put("githubClientId", systemConfigService.getConfigValue("github.oauth2.client-id", ""));
            data.put("githubRedirectUri", systemConfigService.getConfigValue("github.oauth2.redirect-uri", ""));
            data.put("frontendUrl", systemConfigService.getConfigValue("frontend.url", ""));
            data.put("sitePublicSecurityRecordNo", systemConfigService.getConfigValue("site.public-security-record-no", ""));
            data.put("siteIcpLicenseNo", systemConfigService.getConfigValue("site.icp-license-no", ""));
            data.put("siteIcpRecordNo", systemConfigService.getConfigValue("site.icp-record-no", ""));
            data.put("emailSmtpHost", systemConfigService.getConfigValue("email.smtp.host", ""));
            data.put("emailSmtpPort", systemConfigService.getConfigValue("email.smtp.port", ""));
            data.put("emailSmtpUsername", systemConfigService.getConfigValue("email.smtp.username", ""));
            data.put("emailFrom", systemConfigService.getConfigValue("email.from", ""));
            data.put("emailFromDisplayName", systemConfigService.getConfigValue("email.from.display-name", ""));
            data.put("emailEnabled", "true".equalsIgnoreCase(systemConfigService.getConfigValue("email.enabled", "false")));
            data.put("emailPasswordSet", StringUtils.hasText(systemConfigService.getConfigValue("email.smtp.password", "")));
            return Result.success(data);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/settings/github")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新GitHub OAuth配置")
    @Operation(summary = "更新GitHub OAuth配置")
    public Result<Void> updateGithubSettings(@RequestBody Map<String, String> request) {
        try {
            requireSuperAdmin();
            String clientId = request.get("clientId");
            String redirectUri = request.get("redirectUri");
            String frontendUrl = request.get("frontendUrl");
            String clientSecret = request.get("clientSecret");

            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri) || !StringUtils.hasText(frontendUrl)) {
                return Result.error("Client ID、Redirect URI、前端地址不能为空");
            }

            systemConfigService.setConfigValue("github.oauth2.client-id", clientId.trim(), "GitHub OAuth2 Client ID", false);
            systemConfigService.setConfigValue("github.oauth2.redirect-uri", redirectUri.trim(), "GitHub OAuth2 Redirect URI", false);
            systemConfigService.setConfigValue("frontend.url", frontendUrl.trim(), "前端地址", false);
            if (StringUtils.hasText(clientSecret)) {
                systemConfigService.setConfigValue("github.oauth2.client-secret", clientSecret.trim(), "GitHub OAuth2 Client Secret", true);
            }
            return Result.success("GitHub 配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/settings/site")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新站点备案配置")
    @Operation(summary = "更新站点备案配置")
    public Result<Void> updateSiteSettings(@RequestBody Map<String, String> request) {
        try {
            requireSuperAdmin();
            systemConfigService.setConfigValue("site.public-security-record-no",
                    toSafeValue(request.get("publicSecurityRecordNo")), "站点公网安备号", false);
            systemConfigService.setConfigValue("site.icp-license-no",
                    toSafeValue(request.get("icpLicenseNo")), "站点ICP证号", false);
            systemConfigService.setConfigValue("site.icp-record-no",
                    toSafeValue(request.get("icpRecordNo")), "站点ICP备案号", false);
            return Result.success("站点备案配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/settings/email")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新邮箱配置")
    @Operation(summary = "更新邮箱配置")
    public Result<Void> updateEmailSettings(@RequestBody Map<String, Object> request) {
        try {
            requireSuperAdmin();
            systemConfigService.setConfigValue("email.smtp.host",
                    toSafeValue((String) request.get("smtpHost")), "SMTP 主机", false);
            systemConfigService.setConfigValue("email.smtp.port",
                    toSafeValue((String) request.get("smtpPort")), "SMTP 端口", false);
            systemConfigService.setConfigValue("email.smtp.username",
                    toSafeValue((String) request.get("smtpUsername")), "SMTP 用户名", false);
            if (request.get("smtpPassword") instanceof String pwd && StringUtils.hasText(pwd)) {
                systemConfigService.setConfigValue("email.smtp.password", pwd.trim(), "SMTP 密码", true);
            }
            systemConfigService.setConfigValue("email.from",
                    toSafeValue((String) request.get("fromEmail")), "发件人邮箱", false);
            systemConfigService.setConfigValue("email.from.display-name",
                    toSafeValue((String) request.get("fromDisplayName")), "发件人显示名称", false);
            Object enabledObj = request.get("enabled");
            boolean enabled = enabledObj instanceof Boolean b && b;
            systemConfigService.setConfigValue("email.enabled", String.valueOf(enabled), "邮箱通知开关", false);
            return Result.success("邮箱配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
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
            requireSuperAdmin();
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
    
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("用户未登录");
        }
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        User user = userService.getUserByGithubId(githubId);
        if (user == null) {
            throw new SecurityException("用户不存在");
        }
        return user;
    }

    /** 系统配置类接口仅 SUPER_ADMIN 可访问（与拥有 CONFIG_* 权限的 ADMIN 区分） */
    private void requireSuperAdmin() {
        User user = getCurrentUser();
        if (!securityUtils.isSuperAdmin(user.getId())) {
            throw new SecurityException("仅超级管理员可操作");
        }
    }

    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    private String toSafeValue(String value) {
        return value == null ? "" : value.trim();
    }
}