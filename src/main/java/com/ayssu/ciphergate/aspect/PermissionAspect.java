package com.ayssu.ciphergate.aspect;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限检查切面（同时支持 OAuth2 和密码登录用户）
 */
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private UserService userService;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        Authentication authentication = getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("用户未登录");
        }

        // 获取当前用户（兼容 OAuth2 和密码登录两种方式）
        User user = resolveUser(authentication);

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

    /**
     * 获取当前认证信息，优先从 SecurityContext，为空则从 Session 恢复
     */
    private Authentication getAuthentication() {
        // 优先从 SecurityContext 获取
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth;
        }

        // SecurityContext 为空（密码登录场景 JDBC 序列化失败），从 Session 恢复
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpSession session = attrs.getRequest().getSession(false);
            if (session != null) {
                Object passwordAuth = session.getAttribute("passwordAuth");
                if (passwordAuth instanceof Authentication sessionAuth && sessionAuth.isAuthenticated()) {
                    // 恢复到 SecurityContext，后续代码也能用
                    SecurityContextHolder.getContext().setAuthentication(sessionAuth);
                    return sessionAuth;
                }
            }
        }

        return null;
    }

    /**
     * 从 Authentication 中解析 User 对象，兼容 OAuth2 和密码登录
     */
    private User resolveUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        // 密码登录：principal 就是 User 对象
        if (principal instanceof User user) {
            return user;
        }

        // OAuth2 登录：principal 是 OAuth2User，需要通过 githubId 查库
        if (principal instanceof OAuth2User oauth2User) {
            Object idObj = oauth2User.getAttribute("id");
            String githubId = idObj != null ? idObj.toString() : null;
            if (githubId != null) {
                return userService.getUserByGithubId(githubId);
            }
        }

        return null;
    }
}
