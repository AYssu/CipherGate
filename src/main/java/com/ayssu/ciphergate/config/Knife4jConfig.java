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
     * 其他 API 分组
     */
    @Bean
    public GroupedOpenApi otherApi() {
        return GroupedOpenApi.builder()
                .group("9. 其他接口")
                .pathsToMatch("/api/**")
                .pathsToExclude(
                        "/api/user/**", "/api/users/**",
                        "/api/roles/**", "/api/menus/**", "/api/permissions/**", "/api/config/**",
                        "/api/activity/**",
                        "/api/messages/**",
                        "/api/applications/**"
                )
                .build();
    }
}
