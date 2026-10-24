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
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class PortalSecurityConfig {

    private final PortalJwtAuthenticationFilter portalJwtFilter;

    @Bean
    public FilterRegistrationBean<PortalJwtAuthenticationFilter> portalJwtServletRegistration() {
        var registration = new FilterRegistrationBean<>(portalJwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new AndRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/api/portal/**"),
                new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/portal/admin/**")),
                new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/portal/epay-config/**"))))
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
                .anyRequest().hasRole("PORTAL_USER")
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
