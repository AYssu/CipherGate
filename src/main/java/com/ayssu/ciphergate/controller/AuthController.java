package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.entity.User;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    @GetMapping("/user")
    public Map<String, Object> user(@AuthenticationPrincipal OAuth2User principal, HttpSession session) {
        if (principal != null) {
            // 打印所有 GitHub 返回的用户信息
            log.info("=== GitHub OAuth2 用户信息 ===");
            log.info("所有属性: {}", principal.getAttributes());
            log.info("=== 结束 GitHub 用户信息 ===");
            
            return principal.getAttributes();
        }
        
        // 如果 OAuth2User 为空，尝试从 Session 获取
        @SuppressWarnings("unchecked")
        Map<String, Object> githubUser = (Map<String, Object>) session.getAttribute("githubUser");
        
        if (githubUser != null) {
            log.info("从 Session 获取用户信息: {}", githubUser);
        } else {
            log.warn("用户未登录或 Session 已过期");
        }
        
        return githubUser;
    }
    
    @GetMapping("/user/profile")
    public User userProfile(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            log.info("从 Session 获取用户档案: {}", user);
        } else {
            log.warn("Session 中没有用户档案信息");
        }
        return user;
    }
}