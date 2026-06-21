package com.ayssu.ciphergate.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/webjars/**",
                                "/api/config/init/status",
                                "/api/config/init",
                                "/api/config/public/site-info",
                                "/api/config/public/oauth2-login",
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
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**").permitAll()
                        // Swagger(OpenAPI) 文档只允许超级管理员访问
                        .requestMatchers("/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-resources/**").hasAuthority("ROLE_SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
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
}