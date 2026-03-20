package com.ayssu.ciphergate.aspect;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 权限检查切面
 */
@Aspect
@Component
public class PermissionAspect {
    
    @Autowired
    private UserService userService;
    
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("用户未登录");
        }
        
        // 获取当前用户
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        User user = userService.getUserByGithubId(githubId);
        
        if (user == null) {
            throw new SecurityException("用户不存在");
        }
        
        // 检查权限
        boolean hasPermission = userService.hasPermission(user.getId(), requirePermission.value());
        
        if (!hasPermission) {
            throw new SecurityException("权限不足：" + requirePermission.description());
        }
        
        return joinPoint.proceed();
    }
}