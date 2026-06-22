package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.GeoIpService;
import com.ayssu.ciphergate.service.Ip2RegionService;
import com.ayssu.ciphergate.service.MinioObjectService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "系统配置与初始化相关接口")
public class ConfigController {
    
    private final SystemConfigService systemConfigService;
    private final GeoIpService geoIpService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;
    private final Environment environment;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final Ip2RegionService ip2RegionService;
    
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

    @Value("${app.backend.public-base-url:}")
    private String backendPublicBaseUrl;

    @Value("${server.port:8080}")
    private int serverPort;

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
            geoIpService.reloadReaders();
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

    /**
     * 登录页 GitHub OAuth：返回授权跳转地址与已配置的前端地址（无需登录）
     */
    @GetMapping("/public/oauth2-login")
    @Operation(summary = "获取 GitHub OAuth 登录跳转信息（公开）")
    public Result<Map<String, Object>> getPublicOAuth2Login() {
        String baseUrl = resolveOAuth2BackendBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String oauth2AuthorizationUrl = baseUrl + resolveOAuth2AuthorizationPath();
        String frontendUrl = systemConfigService.getFrontendUrl();
        return Result.success(Map.of(
                "oauth2AuthorizationUrl", oauth2AuthorizationUrl,
                "frontendUrl", frontendUrl == null ? "" : frontendUrl.trim()
        ));
    }

    /**
     * 从全量 redirect-uri 解析后端根 URL；否则使用可选 app.backend.public-base-url；再回退本地默认端口。
     */
    private String resolveOAuth2BackendBaseUrl() {
        String redirectUri = systemConfigService.getGithubRedirectUri();
        if (StringUtils.hasText(redirectUri) && redirectUri.startsWith("http")) {
            try {
                URI uri = URI.create(redirectUri.trim());
                String path = uri.getPath();
                if (path != null && path.contains("/login/oauth2/code")) {
                    return uri.getScheme() + "://" + uri.getAuthority();
                }
            } catch (Exception e) {
                log.warn("从 github.oauth2.redirect-uri 解析后端地址失败: {}", redirectUri);
            }
        }
        if (StringUtils.hasText(backendPublicBaseUrl)) {
            return backendPublicBaseUrl.trim();
        }
        return "http://localhost:" + serverPort;
    }

    /**
     * 根据已配置回调路径自动选择 OAuth2 授权入口路径，确保与反向代理前缀保持一致。
     */
    private String resolveOAuth2AuthorizationPath() {
        String redirectUri = systemConfigService.getGithubRedirectUri();
        if (StringUtils.hasText(redirectUri) && redirectUri.startsWith("http")) {
            try {
                URI uri = URI.create(redirectUri.trim());
                String path = uri.getPath();
                if (StringUtils.hasText(path) && path.startsWith("/api/login/oauth2/code")) {
                    return "/api/oauth2/authorization/github";
                }
            } catch (Exception e) {
                log.warn("从 github.oauth2.redirect-uri 解析授权入口失败: {}", redirectUri);
            }
        }
        return "/oauth2/authorization/github";
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
            data.put("geoIpEnabled", geoIpService.isEnabled());
            String countryKey = systemConfigService.getConfigValue(GeoIpService.CONFIG_GEOIP_COUNTRY_OBJECT_KEY, "");
            String cityKey = systemConfigService.getConfigValue(GeoIpService.CONFIG_GEOIP_CITY_OBJECT_KEY, "");
            data.put("geoIpCountryUploaded", minioObjectService.contentLengthDefaultBucket(countryKey) > 0);
            data.put("geoIpCityUploaded", minioObjectService.contentLengthDefaultBucket(cityKey) > 0);
            data.put("geoIpReady", geoIpService.isReady());
            data.put("geoIpLastError", geoIpService.getLastError());
            data.put("ip2RegionEnabled", ip2RegionService.isEnabled());
            String ip2RegionKey = systemConfigService.getConfigValue(Ip2RegionService.CONFIG_IP2REGION_OBJECT_KEY, "");
            data.put("ip2RegionUploaded", minioObjectService.contentLengthDefaultBucket(ip2RegionKey) > 0);
            data.put("ip2RegionReady", ip2RegionService.isReady());
            data.put("ip2RegionLastError", ip2RegionService.getLastError());
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

    @PostMapping("/settings/geoip")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新IP地理解析开关")
    @Operation(summary = "更新IP地理解析开关")
    public Result<Void> updateGeoIpSettings(@RequestBody Map<String, Object> request) {
        try {
            requireSuperAdmin();
            Object enabledObj = request.get("enabled");
            boolean enabled = enabledObj instanceof Boolean b && b;
            systemConfigService.setConfigValue(GeoIpService.CONFIG_GEOIP_ENABLED, String.valueOf(enabled), "IP 地理解析开关", false);
            geoIpService.reloadReaders();
            if (enabled && !geoIpService.isReady()) {
                return Result.error("已开启但 GeoIP 未就绪: " + (geoIpService.getLastError() == null ? "请先上传 mmdb 文件" : geoIpService.getLastError()));
            }
            return Result.success("GeoIP 配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/settings/geoip/upload/{dbType}")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "上传IP地理数据库")
    @Operation(summary = "上传 GeoIP 数据库文件（country/city）")
    public Result<Void> uploadGeoIpDb(@PathVariable String dbType, @RequestParam("file") MultipartFile file) {
        try {
            requireSuperAdmin();
            if (file == null || file.isEmpty()) {
                return Result.error("上传文件不能为空");
            }
            if (!minioProperties.isEnabled()) {
                return Result.error("MinIO 未启用，无法上传 GeoIP 数据库");
            }
            String lowerType = dbType == null ? "" : dbType.trim().toLowerCase();
            if (!"country".equals(lowerType) && !"city".equals(lowerType)) {
                return Result.error("dbType 仅支持 country 或 city");
            }
            String originalFilename = file.getOriginalFilename();
            if (!StringUtils.hasText(originalFilename) || !originalFilename.toLowerCase().endsWith(".mmdb")) {
                return Result.error("仅支持 .mmdb 文件");
            }
            String objectKey = "country".equals(lowerType) ? "geoip/GeoLite2-Country.mmdb" : "geoip/GeoLite2-City.mmdb";
            minioObjectService.uploadBinaryDefaultBucket(objectKey, file, "application/octet-stream");
            String configKey = "country".equals(lowerType) ? GeoIpService.CONFIG_GEOIP_COUNTRY_OBJECT_KEY : GeoIpService.CONFIG_GEOIP_CITY_OBJECT_KEY;
            String desc = "country".equals(lowerType) ? "GeoIP 国家库对象键" : "GeoIP 城市库对象键";
            systemConfigService.setConfigValue(configKey, objectKey, desc, false);
            geoIpService.reloadReaders();
            return Result.success("GeoIP 文件上传成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传 GeoIP 数据库失败: {}", e.getMessage(), e);
            return Result.error("上传 GeoIP 数据库失败: " + e.getMessage());
        }
    }

    @PostMapping("/settings/ip2region")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新ip2region开关")
    @Operation(summary = "更新ip2region开关")
    public Result<Void> updateIp2RegionSettings(@RequestBody Map<String, Object> request) {
        try {
            requireSuperAdmin();
            Object enabledObj = request.get("enabled");
            boolean enabled = enabledObj instanceof Boolean b && b;
            systemConfigService.setConfigValue(Ip2RegionService.CONFIG_IP2REGION_ENABLED, String.valueOf(enabled), "ip2region 开关", false);
            ip2RegionService.reloadSearcher();
            if (enabled && !ip2RegionService.isReady()) {
                return Result.error("已开启但 ip2region 未就绪: " + (ip2RegionService.getLastError() == null ? "请先上传 xdb 文件" : ip2RegionService.getLastError()));
            }
            return Result.success("ip2region 配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/settings/ip2region/upload")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "上传ip2region数据库")
    @Operation(summary = "上传 ip2region 数据库文件（xdb）")
    public Result<Void> uploadIp2RegionDb(@RequestParam("file") MultipartFile file) {
        try {
            requireSuperAdmin();
            if (file == null || file.isEmpty()) {
                return Result.error("上传文件不能为空");
            }
            if (!minioProperties.isEnabled()) {
                return Result.error("MinIO 未启用，无法上传 ip2region 数据库");
            }
            String originalFilename = file.getOriginalFilename();
            if (!StringUtils.hasText(originalFilename) || !originalFilename.toLowerCase().endsWith(".xdb")) {
                return Result.error("仅支持 .xdb 文件");
            }
            String objectKey = "ip2region/ip2region.xdb";
            minioObjectService.uploadBinaryDefaultBucket(objectKey, file, "application/octet-stream");
            systemConfigService.setConfigValue(Ip2RegionService.CONFIG_IP2REGION_OBJECT_KEY, objectKey, "ip2region 数据库对象键", false);
            ip2RegionService.reloadSearcher();
            return Result.success("ip2region 文件上传成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传 ip2region 数据库失败: {}", e.getMessage(), e);
            return Result.error("上传 ip2region 数据库失败: " + e.getMessage());
        }
    }

    @GetMapping("/settings/invite")
    @Operation(summary = "获取邀请有奖配置")
    public Result<Map<String, Object>> getInviteSettings() {
        try {
            Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("enabled", Boolean.parseBoolean(systemConfigService.getConfigValue("invite.enabled", "true")));
            settings.put("maxCount", Integer.parseInt(systemConfigService.getConfigValue("invite.max-count", "20")));
            settings.put("rewardAmount", Long.parseLong(systemConfigService.getConfigValue("invite.reward-amount", "300")));
            return Result.success(settings);
        } catch (Exception e) {
            return Result.error("获取邀请配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/settings/invite")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新邀请有奖配置")
    @Operation(summary = "更新邀请有奖配置")
    public Result<Void> updateInviteSettings(@RequestBody Map<String, Object> request) {
        try {
            requireSuperAdmin();
            if (request.containsKey("enabled")) {
                systemConfigService.setConfigValue("invite.enabled", String.valueOf(request.get("enabled")), "邀请功能开关", false);
            }
            if (request.containsKey("maxCount")) {
                systemConfigService.setConfigValue("invite.max-count", String.valueOf(request.get("maxCount")), "最大邀请人数", false);
            }
            if (request.containsKey("rewardAmount")) {
                systemConfigService.setConfigValue("invite.reward-amount", String.valueOf(request.get("rewardAmount")), "邀请奖励金额(分)", false);
            }
            return Result.success("邀请配置更新成功", null);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("更新邀请配置失败: " + e.getMessage());
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
        User user = AuthUtils.getCurrentUser();
        if (user != null) return user;

        Authentication authentication = AuthUtils.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String githubId = oauth2User.getAttribute("id").toString();
            user = userService.getUserByGithubId(githubId);
            if (user != null) return user;
        }

        throw new SecurityException("用户未登录");
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

    // ==================== 支付配置 ====================

    @GetMapping("/payment")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取支付配置")
    public Result<Map<String, Object>> getPaymentConfig() {
        try {
            requireSuperAdmin();
            Map<String, Object> data = new HashMap<>();
            data.put("epayUrl", systemConfigService.getConfigValue("payment.epay.url", ""));
            data.put("epayPid", systemConfigService.getConfigValue("payment.epay.pid", ""));
            data.put("epayKeySet", StringUtils.hasText(systemConfigService.getConfigValue("payment.epay.key", "")));
            data.put("epayNotifyUrl", systemConfigService.getConfigValue("payment.epay.notify.url", ""));
            data.put("epayReturnUrl", systemConfigService.getConfigValue("payment.epay.return.url", ""));
            data.put("successRedirectUrl", systemConfigService.getConfigValue("payment.success.redirect.url", "/user/balance"));
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取支付配置失败: {}", e.getMessage());
            return Result.error("获取支付配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/payment")
    @RequirePermission("CONFIG_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "SYSTEM_CONFIG", description = "更新支付配置")
    @Operation(summary = "更新支付配置")
    public Result<Void> updatePaymentConfig(@RequestBody Map<String, String> body) {
        try {
            requireSuperAdmin();
            if (body.containsKey("epayUrl")) {
                systemConfigService.setConfigValue("payment.epay.url", toSafeValue(body.get("epayUrl")), "易支付接口地址", false);
            }
            if (body.containsKey("epayPid")) {
                systemConfigService.setConfigValue("payment.epay.pid", toSafeValue(body.get("epayPid")), "易支付商户ID", false);
            }
            if (body.containsKey("epayKey")) {
                systemConfigService.setConfigValue("payment.epay.key", toSafeValue(body.get("epayKey")), "易支付密钥", true);
            }
            if (body.containsKey("epayNotifyUrl")) {
                systemConfigService.setConfigValue("payment.epay.notify.url", toSafeValue(body.get("epayNotifyUrl")), "易支付异步回调地址", false);
            }
            if (body.containsKey("epayReturnUrl")) {
                systemConfigService.setConfigValue("payment.epay.return.url", toSafeValue(body.get("epayReturnUrl")), "易支付同步跳转地址", false);
            }
            if (body.containsKey("successRedirectUrl")) {
                systemConfigService.setConfigValue("payment.success.redirect.url", toSafeValue(body.get("successRedirectUrl")), "支付成功跳转地址", false);
            }
            systemConfigService.refreshCache();
            return Result.success("支付配置更新成功", null);
        } catch (Exception e) {
            log.error("更新支付配置失败: {}", e.getMessage());
            return Result.error("更新支付配置失败: " + e.getMessage());
        }
    }
}
