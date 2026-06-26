package com.ayssu.ciphergate.portal.config;

import com.ayssu.ciphergate.portal.filter.PortalJwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class PortalSecurityConfig {

    private final PortalJwtAuthenticationFilter portalJwtFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/portal/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/portal/auth/captcha",
                    "/api/portal/auth/login",
                    "/api/portal/auth/recovery/**",
                    "/api/portal/auth/verify-email-code",
                    "/api/portal/payment/notify",
                    "/api/portal/payment/return"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(portalJwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或令牌已过期\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"权限不足\"}");
                })
            );

        return http.build();
    }
}
