package com.ayssu.ciphergate.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
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
                        .description("CipherGate 企业级网络安全智能防护平台 API 接口文档")
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
                ));
    }
}
