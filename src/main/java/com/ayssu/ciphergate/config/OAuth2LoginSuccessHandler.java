package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private final UserService userService;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        
        log.info("=== OAuth2 登录成功处理器 ===");
        log.info("认证主体类型: {}", authentication.getPrincipal().getClass().getName());
        log.info("OAuth2User 所有属性: {}", oauth2User.getAttributes());
        
        // 详细打印每个属性
        oauth2User.getAttributes().forEach((key, value) -> {
            log.info("属性 [{}]: {} (类型: {})", 
                key, 
                String.valueOf(value), 
                value != null ? value.getClass().getSimpleName() : "null");
        });
        
        // 保存或更新用户信息到数据库
        User user = userService.findOrCreateUser(oauth2User);
        log.info("保存/更新用户信息: {}", user);
        
        // 将用户信息存储到 Session 中
        HttpSession session = request.getSession();
        session.setAttribute("user", user);
        session.setAttribute("githubUser", oauth2User.getAttributes());
        
        // 设置 Session 最大不活跃时间（7天）
        session.setMaxInactiveInterval(7 * 24 * 60 * 60);
        
        log.info("Session ID: {}", session.getId());
        log.info("Session 最大不活跃时间: {} 秒", session.getMaxInactiveInterval());
        log.info("=== OAuth2 登录处理完成 ===");
        
        // 重定向到前端仪表板
        getRedirectStrategy().sendRedirect(request, response, "http://localhost:5173/dashboard");
    }
}