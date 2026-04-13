package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SystemConfigService systemConfigService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception) throws IOException, ServletException {
        
        log.error("=== OAuth2 登录失败 ===");
        log.error("异常类型: {}", exception.getClass().getName());
        log.error("异常消息: {}", exception.getMessage());
        
        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2AuthenticationException oauth2Exception = (OAuth2AuthenticationException) exception;
            log.error("OAuth2 错误代码: {}", oauth2Exception.getError().getErrorCode());
            log.error("OAuth2 错误描述: {}", oauth2Exception.getError().getDescription());
            log.error("OAuth2 错误 URI: {}", oauth2Exception.getError().getUri());
        }
        
        log.error("完整异常信息: ", exception);
        log.error("=== OAuth2 登录失败处理完成 ===");
        
        // 重定向到前端首页并显示错误，避免落到后端 "/" 触发 Whitelabel 404
        getRedirectStrategy().sendRedirect(request, response, resolveFrontendFailureUrl());
    }

    private String resolveFrontendFailureUrl() {
        String configured = systemConfigService.getFrontendUrl();
        if (!StringUtils.hasText(configured)) {
            return "http://localhost:5173/?error=oauth2_failed";
        }
        try {
            URI uri = new URI(configured.trim());
            String base = uri.getScheme() + "://" + uri.getAuthority();
            return base + "/?error=oauth2_failed";
        } catch (URISyntaxException e) {
            log.warn("前端地址格式异常，回退默认失败重定向地址: {}", configured);
            return "http://localhost:5173/?error=oauth2_failed";
        }
    }
}
