package com.ayssu.ciphergate.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    
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
        
        // 重定向到首页并显示错误
        getRedirectStrategy().sendRedirect(request, response, "/?error=oauth2_failed");
    }
}
