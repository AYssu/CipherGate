package com.ayssu.ciphergate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 7 * 24 * 60 * 60) // 7天
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        
        // Cookie 名称
        serializer.setCookieName("CIPHERGATE_SESSION");
        
        // Cookie 路径
        serializer.setCookiePath("/");
        
        // 域名设置（开发环境可以不设置）
        // serializer.setDomainName("localhost");
        
        // HttpOnly 防止 XSS 攻击
        serializer.setUseHttpOnlyCookie(true);
        
        // 生产环境启用 Secure（需要 HTTPS）
        serializer.setUseSecureCookie(false); // 开发环境设为 false
        
        // SameSite 设置
        serializer.setSameSite("Lax");
        
        // Cookie 最大存活时间（7天）
        serializer.setCookieMaxAge(7 * 24 * 60 * 60);
        
        return serializer;
    }
}