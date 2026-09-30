package com.ayssu.ciphergate.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Knife4j API 文档配置。
 * <p>
 * 除接口分组外，这里还统一声明三套认证方式：
 * <ul>
 *     <li>管理端接口：{@code CIPHERGATE_SESSION} Cookie</li>
 *     <li>用户门户接口：Bearer JWT</li>
 *     <li>{@code /api/v1} 第三方接口：X-App-Key / X-Timestamp / X-Nonce / X-Signature</li>
 * </ul>
 * 安全要求按实际路径添加，避免公开接口、登录接口和支付回调被错误标记为必须登录。
 */
@Configuration
public class Knife4jConfig {

    private static final String SESSION_COOKIE_SCHEME = "sessionCookie";
    private static final String PORTAL_BEARER_SCHEME = "portalBearer";
    private static final String APP_KEY_SCHEME = "xAppKey";
    private static final String TIMESTAMP_SCHEME = "xTimestamp";
    private static final String NONCE_SCHEME = "xNonce";
    private static final String SIGNATURE_SCHEME = "xSignature";
    private static final String UPDATE_TICKET_SCHEME = "updateTicket";

    /**
     * 与 SecurityConfig / PortalSecurityConfig 中 permitAll 的 HTTP 接口保持一致。
     */
    private static final String[] PUBLIC_PATH_PATTERNS = {
            "/api/config/init/status",
            "/api/config/init",
            "/api/config/public/site-info",
            "/api/config/public/oauth2-login",
            "/api/config/public/invite-status",
            "/api/test",
            "/api/open/**",
            "/api/public/app-user/register/**",
            "/api/public/app-user/self/**",
            "/api/public/license/**",
            "/api/user/status",
            "/api/auth/login",
            "/api/payment/notify",
            "/api/payment/return",
            "/api/portal/auth/captcha",
            "/api/portal/auth/login",
            "/api/portal/auth/recovery/**",
            "/api/portal/auth/verify-email-code",
            "/api/portal/payment/notify",
            "/api/portal/payment/return",
            "/api/oauth2/authorization/**",
            "/api/login/oauth2/code/**"
    };

    /**
     * 已有独立分组的路径，用于“其他接口”兜底时排除，避免同一接口重复出现。
     */
    private static final String[] GROUPED_PATHS = {
            "/api/auth/**", "/api/user/**", "/api/users/**", "/api/oauth2/**",
            "/api/roles/**", "/api/menus/**", "/api/permissions/**", "/api/config/**", "/api/system/**",
            "/api/activity/**", "/api/dashboard/**",
            "/api/messages/**", "/api/announcements/**", "/api/doc/**",
            "/api/applications/**",
            "/api/app-variables/**",
            "/api/app-users/**", "/api/licenses/**",
            "/api/plugins/**", "/api/plugin-test/**", "/api/function-plugins/**",
            "/api/membership/**", "/api/quota-products/**",
            "/api/payment/**", "/api/admin/payment/**", "/api/tickets/**", "/api/admin/tickets/**",
            "/api/portal/**",
            "/api/third-party/**",
            "/api/open/**", "/api/public/**",
            "/api/upload/**",
            "/api/v1/**",
            "/api/github/**", "/api/debug/**", "/api/oauth2/test/**",
            "/api/test"
    };

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CipherGate API 文档")
                        .version("1.0.0")
                        .description("""
                                CipherGate 企业级授权与安全管控平台 OpenAPI 接口文档。

                                ## 功能范围

                                - 身份认证：GitHub OAuth2、密码降级登录、用户门户 JWT
                                - 用户与权限：用户、角色、菜单、权限、活动日志、仪表盘
                                - 应用与授权：应用、终端用户、卡密、变量、代理与配额
                                - 商业能力：会员、套餐、支付、订单、工单
                                - 平台能力：插件、文件上传、文档中心、公告、系统配置
                                - 对外集成：公开注册/查询、用户门户、第三方签名接口

                                ## 认证方式

                                1. **管理端接口**：使用 `CIPHERGATE_SESSION` Cookie；先调用登录接口，浏览器会自动保存会话 Cookie。
                                2. **用户门户 `/api/portal/**`**：使用 `Authorization: Bearer <JWT>`。
                                3. **第三方 `/api/v1/**`**：通常需要 `X-App-Key`、`X-Timestamp`、`X-Nonce`、`X-Signature` 四个请求头；更新包下载和部分特殊接口使用各自说明的票据/报文凭证。
                                4. 标记为公开的登录、注册、支付回调、初始化等接口无需认证。

                                ## 统一响应

                                大多数 JSON 接口返回：

                                ```json
                                {
                                  "code": 200,
                                  "message": "操作成功",
                                  "data": {},
                                  "success": true,
                                  "timestamp": "2026-09-30 12:00:00"
                                }
                                ```

                                常见状态码：`200` 成功、`400` 参数错误、`401` 未登录、`403` 无权限、`404` 资源不存在、`500` 服务错误。

                                ## 使用说明

                                - 生产环境根地址：`https://www.ayssu.com`，接口路径已包含 `/api` 前缀。
                                - 本页面及 OpenAPI JSON 仅超级管理员可访问。
                                - 列表接口的分页参数以各接口说明为准，常见为 `page/size` 或 `pageNum/pageSize`。
                                """)
                        .contact(new Contact()
                                .name("Ayssu")
                                .url("https://github.com/AYssu/CipherGate")))
                .servers(List.of(
                        new Server()
                                .url("https://www.ayssu.com")
                                .description("生产环境"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发环境")
                ))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("CIPHERGATE_SESSION")
                                .description("管理端会话 Cookie，可通过密码登录或 GitHub OAuth2 登录获取"))
                        .addSecuritySchemes(PORTAL_BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("用户门户登录后签发的 JWT"))
                        .addSecuritySchemes(APP_KEY_SCHEME, headerScheme("X-App-Key", "应用标识 appKey"))
                        .addSecuritySchemes(TIMESTAMP_SCHEME, headerScheme("X-Timestamp", "毫秒时间戳，服务端允许少量时钟偏移"))
                        .addSecuritySchemes(NONCE_SCHEME, headerScheme("X-Nonce", "随机字符串，用于防重放"))
                        .addSecuritySchemes(SIGNATURE_SCHEME, headerScheme("X-Signature", "HMAC-SHA256 十六进制签名，签名算法见第三方接口说明"))
                        .addSecuritySchemes(UPDATE_TICKET_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.QUERY)
                                .name("ticket")
                                .description("检查更新接口返回的短时下载票据")));
    }

    /**
     * 按路径为操作补充认证方式和通用的认证失败响应。
     */
    @Bean
    public OpenApiCustomizer apiSecurityCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null) {
                    return;
                }
                pathItem.readOperationsMap().forEach((method, operation) -> applySecurity(path, operation));
            });
        };
    }

    private void applySecurity(String path, Operation operation) {
        SecurityRequirement requirement = securityRequirementFor(path);
        if (requirement == null) {
            return;
        }

        operation.addSecurityItem(requirement);
        if (operation.getResponses() == null) {
            return;
        }
        if (!operation.getResponses().containsKey("401")) {
            operation.getResponses().put("401", new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description("未登录、会话过期、令牌无效或第三方签名验证失败"));
        }
        if (!operation.getResponses().containsKey("403")) {
            operation.getResponses().put("403", new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description("权限不足、资源被禁用或额度不足"));
        }
    }

    private SecurityRequirement securityRequirementFor(String path) {
        if (isPublicPath(path)) {
            return null;
        }

        if (pathMatcher.match("/api/portal/**", path)) {
            return requirement(PORTAL_BEARER_SCHEME);
        }

        if (pathMatcher.match("/api/v1/**", path)) {
            if (pathMatcher.match("/api/v1/app/update-package", path)) {
                return requirement(UPDATE_TICKET_SCHEME);
            }
            if (pathMatcher.match("/api/v1/third_party/recharge", path)) {
                // apiKey/sign 位于请求体，OpenAPI 无法用标准 securityScheme 准确表达，操作说明中单独注明。
                return null;
            }
            return requirement(APP_KEY_SCHEME, TIMESTAMP_SCHEME, NONCE_SCHEME, SIGNATURE_SCHEME);
        }

        if (path.startsWith("/api/") || path.equals("/user") || path.startsWith("/user/")) {
            return requirement(SESSION_COOKIE_SCHEME);
        }

        return null;
    }

    private boolean isPublicPath(String path) {
        for (String pattern : PUBLIC_PATH_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private SecurityRequirement requirement(String... schemes) {
        SecurityRequirement requirement = new SecurityRequirement();
        for (String scheme : schemes) {
            requirement.addList(scheme);
        }
        return requirement;
    }

    private SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }

    /**
     * 认证与个人中心 API 分组
     */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("01. 认证与个人中心")
                .pathsToMatch("/api/auth/**", "/api/user/**", "/api/oauth2/**", "/user", "/user/**")
                .build();
    }

    /**
     * 用户与权限 API 分组
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("02. 用户与权限")
                .pathsToMatch("/api/users/**", "/api/roles/**", "/api/menus/**", "/api/permissions/**")
                .build();
    }

    /**
     * 系统管理 API 分组
     */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("03. 系统管理")
                .pathsToMatch("/api/config/**", "/api/system/**")
                .build();
    }

    /**
     * 仪表盘与日志 API 分组
     */
    @Bean
    public GroupedOpenApi activityApi() {
        return GroupedOpenApi.builder()
                .group("04. 仪表盘与日志")
                .pathsToMatch("/api/activity/**", "/api/dashboard/**")
                .build();
    }

    /**
     * 消息、公告与文档 API 分组
     */
    @Bean
    public GroupedOpenApi messageApi() {
        return GroupedOpenApi.builder()
                .group("05. 消息、公告与文档")
                .pathsToMatch("/api/messages/**", "/api/announcements/**", "/api/doc/**")
                .build();
    }

    /**
     * 应用管理 API 分组
     */
    @Bean
    public GroupedOpenApi applicationApi() {
        return GroupedOpenApi.builder()
                .group("06. 应用管理")
                .pathsToMatch("/api/applications/**")
                .build();
    }

    /**
     * 应用变量 API 分组
     */
    @Bean
    public GroupedOpenApi appVariableApi() {
        return GroupedOpenApi.builder()
                .group("07. 应用变量")
                .pathsToMatch("/api/app-variables/**")
                .build();
    }

    /**
     * 终端用户与卡密 API 分组
     */
    @Bean
    public GroupedOpenApi appUserApi() {
        return GroupedOpenApi.builder()
                .group("08. 终端用户与卡密")
                .pathsToMatch("/api/app-users/**", "/api/licenses/**")
                .build();
    }

    /**
     * 插件与功能模块 API 分组
     */
    @Bean
    public GroupedOpenApi pluginApi() {
        return GroupedOpenApi.builder()
                .group("09. 插件与功能模块")
                .pathsToMatch("/api/plugins/**", "/api/plugin-test/**", "/api/function-plugins/**")
                .build();
    }

    /**
     * 会员与配额 API 分组
     */
    @Bean
    public GroupedOpenApi membershipApi() {
        return GroupedOpenApi.builder()
                .group("10. 会员与配额")
                .pathsToMatch("/api/membership/**", "/api/quota-products/**")
                .build();
    }

    /**
     * 支付与工单 API 分组
     */
    @Bean
    public GroupedOpenApi paymentApi() {
        return GroupedOpenApi.builder()
                .group("11. 支付与工单")
                .pathsToMatch(
                        "/api/payment/**",
                        "/api/admin/payment/**",
                        "/api/tickets/**",
                        "/api/admin/tickets/**"
                )
                .build();
    }

    /**
     * 用户门户 API 分组
     */
    @Bean
    public GroupedOpenApi portalApi() {
        return GroupedOpenApi.builder()
                .group("12. 用户门户")
                .pathsToMatch("/api/portal/**")
                .build();
    }

    /**
     * 第三方管理 API 分组
     */
    @Bean
    public GroupedOpenApi thirdPartyCredentialApi() {
        return GroupedOpenApi.builder()
                .group("13. 第三方管理")
                .pathsToMatch("/api/third-party/**")
                .build();
    }

    /**
     * 公开与自助 API 分组
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("14. 公开与自助")
                .pathsToMatch("/api/open/**", "/api/public/**")
                .build();
    }

    /**
     * 文件上传 API 分组
     */
    @Bean
    public GroupedOpenApi uploadApi() {
        return GroupedOpenApi.builder()
                .group("15. 文件上传")
                .pathsToMatch("/api/upload/**")
                .build();
    }

    /**
     * 第三方协议 API 分组
     */
    @Bean
    public GroupedOpenApi thirdPartyApi() {
        return GroupedOpenApi.builder()
                .group("16. 第三方协议")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    /**
     * 运维与调试 API 分组
     */
    @Bean
    public GroupedOpenApi opsApi() {
        return GroupedOpenApi.builder()
                .group("98. 运维与调试")
                .pathsToMatch(
                        "/api/github/**",
                        "/api/debug/**",
                        "/api/oauth2/test/**",
                        "/api/test"
                )
                .build();
    }

    /**
     * 其他 API 分组
     */
    @Bean
    public GroupedOpenApi otherApi() {
        return GroupedOpenApi.builder()
                .group("99. 其他接口")
                .pathsToMatch("/api/**")
                .pathsToExclude(GROUPED_PATHS)
                .build();
    }
}
