package com.ayssu.ciphergate.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.ExternalDocumentation;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Knife4j API 文档配置
 */
@Configuration
public class Knife4jConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CipherGate API 文档")
                        .version("1.0.0")
                        .description("CipherGate 企业级网络安全智能防护平台 API 接口文档\n\n" +
                                "## 功能特性\n" +
                                "- 🔐 OAuth2.0 身份认证\n" +
                                "- 🛡️ RBAC 权限管理\n" +
                                "- 📊 活动日志监控\n" +
                                "- 🔔 消息通知系统\n" +
                                "- ⚙️ 系统配置管理")
                        .contact(new Contact()
                                .name("Ayssu")
                                .email("contact@ciphergate.com")
                                .url("https://github.com/ayssu/ciphergate"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发环境"),
                        new Server()
                                .url("https://api.ciphergate.com")
                                .description("生产环境")
                ))
                .externalDocs(new ExternalDocumentation()
                        .description("CipherGate 完整文档")
                        .url("https://docs.ciphergate.com"));
    }
    
    /**
     * 用户管理 API 分组
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("1. 用户管理")
                .pathsToMatch("/api/user/**", "/api/users/**")
                .build();
    }
    
    /**
     * 系统管理 API 分组
     */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("2. 系统管理")
                .pathsToMatch("/api/roles/**", "/api/menus/**", "/api/permissions/**", "/api/config/**")
                .build();
    }
    
    /**
     * 活动日志 API 分组
     */
    @Bean
    public GroupedOpenApi activityApi() {
        return GroupedOpenApi.builder()
                .group("3. 活动日志")
                .pathsToMatch("/api/activity/**")
                .build();
    }
    
    /**
     * 消息通知 API 分组
     */
    @Bean
    public GroupedOpenApi messageApi() {
        return GroupedOpenApi.builder()
                .group("4. 消息通知")
                .pathsToMatch("/api/messages/**")
                .build();
    }
    
    /**
     * 应用管理 API 分组
     */
    @Bean
    public GroupedOpenApi applicationApi() {
        return GroupedOpenApi.builder()
                .group("5. 应用管理")
                .pathsToMatch("/api/applications/**")
                .build();
    }

    /**
     * 应用变量 API 分组
     */
    @Bean
    public GroupedOpenApi appVariableApi() {
        return GroupedOpenApi.builder()
                .group("6. 应用变量")
                .pathsToMatch("/api/app-variables/**")
                .build();
    }

    /**
     * 应用终端用户 API 分组
     */
    @Bean
    public GroupedOpenApi appUserApi() {
        return GroupedOpenApi.builder()
                .group("7. 终端用户")
                .pathsToMatch("/api/app-users/**")
                .build();
    }

    /**
     * 卡密管理 API 分组
     */
    @Bean
    public GroupedOpenApi licenseApi() {
        return GroupedOpenApi.builder()
                .group("8. 卡密管理")
                .pathsToMatch("/api/licenses/**")
                .build();
    }

    /**
     * 插件模块 API 分组
     */
    @Bean
    public GroupedOpenApi pluginApi() {
        return GroupedOpenApi.builder()
                .group("9. 插件模块")
                .pathsToMatch("/api/plugins/**", "/api/plugin-test/**")
                .build();
    }

    /**
     * 三方接口 API 分组
     */
    @Bean
    public GroupedOpenApi thirdPartyApi() {
        return GroupedOpenApi.builder()
                .group("10. 三方接口")
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
                        "/api/system/**",
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
                .pathsToExclude(
                        "/api/user/**", "/api/users/**",
                        "/api/roles/**", "/api/menus/**", "/api/permissions/**", "/api/config/**",
                        "/api/activity/**",
                        "/api/messages/**",
                        "/api/applications/**",
                        "/api/app-variables/**",
                        "/api/app-users/**",
                        "/api/licenses/**",
                        "/api/plugins/**", "/api/plugin-test/**",
                        "/api/v1/**",
                        "/api/system/**", "/api/github/**", "/api/debug/**", "/api/oauth2/test/**",
                        "/api/test"
                )
                .build();
    }
}
