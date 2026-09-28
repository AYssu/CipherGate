package com.ayssu.ciphergate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 7 * 24 * 60 * 60) // 7天
public class SessionConfig {

    @Value("${app.session.cookie-domain:}")
    private String cookieDomain;

    @Value("${app.session.cookie-secure:false}")
    private boolean cookieSecure;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        
        // Cookie 名称
        serializer.setCookieName("CIPHERGATE_SESSION");
        
        // Cookie 路径
        serializer.setCookiePath("/");
        
        // 仅在显式配置时设置域名，避免线上域名与 localhost 冲突导致 Cookie 丢失
        if (StringUtils.hasText(cookieDomain)) {
            serializer.setDomainName(cookieDomain.trim());
        }
        
        // HttpOnly 防止 XSS 攻击
        serializer.setUseHttpOnlyCookie(true);
        
        // 本地 HTTP 默认关闭；生产 HTTPS 通过 APP_SESSION_COOKIE_SECURE=true 开启
        serializer.setUseSecureCookie(cookieSecure);
        
        // SameSite 设置为 Lax（允许顶级导航携带 Cookie）
        serializer.setSameSite("Lax");
        
        // Cookie 最大存活时间（7天）
        serializer.setCookieMaxAge(7 * 24 * 60 * 60);
        
        return serializer;
    }
}