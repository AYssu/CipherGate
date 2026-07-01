package com.ayssu.ciphergate.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyAuthFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final ThirdPartyAuthFilter thirdPartyAuthFilter;
    private final ActiveUserSessionFilter activeUserSessionFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final OAuth2ProxyConfig oAuth2ProxyConfig;
    private final com.ayssu.ciphergate.service.SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        if (oAuth2ProxyConfig.isProxyEnabled()) {
            customOAuth2UserService.configureRestOperations();
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/webjars/**",
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
                                "/api/v1/**",
                                "/api/auth/login",
                                "/api/payment/notify",
                                "/api/payment/return",
                                "/api/portal/payment/notify",
                                "/api/portal/payment/return",
                                "/api/oauth2/authorization/**",
                                "/api/login/oauth2/code/**",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-resources/**").hasAuthority("ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> {
                    oauth2.userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService));
                    if (oAuth2ProxyConfig.isProxyEnabled()) {
                        oauth2.tokenEndpoint(token -> token
                                .accessTokenResponseClient(buildTokenResponseClient()));
                    }
                    oauth2.successHandler(oAuth2LoginSuccessHandler)
                            .failureHandler(oAuth2LoginFailureHandler);
                })
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("CIPHERGATE_SESSION")
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，只有超级管理员可以访问 API 文档\"}");
                        })
                );

        http.addFilterBefore(thirdPartyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(activeUserSessionFilter, ThirdPartyAuthFilter.class);

        return http.build();
    }

    private org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient buildTokenResponseClient() {
        var client = new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        var routingFactory = oAuth2ProxyConfig.createRoutingRequestFactory();
        var restClient = org.springframework.web.client.RestClient.builder()
                .requestFactory(routingFactory)
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new org.springframework.http.converter.FormHttpMessageConverter());
                    converters.addCustomConverter(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler())
                .build();
        client.setRestClient(restClient);
        return client;
    }
}
