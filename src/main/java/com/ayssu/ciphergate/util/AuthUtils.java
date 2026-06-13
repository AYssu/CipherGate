package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 统一获取当前登录认证信息。
 * 密码登录用户的 SecurityContext 无法通过 JDBC Session 恢复时，
 * 从 HTTP Session 中兜底读取。
 */
public final class AuthUtils {

    private AuthUtils() {}

    /**
     * 获取当前认证信息（优先 SecurityContext，兜底 Session）
     */
    public static Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth;
        }

        // 兜底：从 HTTP Session 恢复
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpSession session = attrs.getRequest().getSession(false);
            if (session != null) {
                Object passwordAuth = session.getAttribute("passwordAuth");
                if (passwordAuth instanceof Authentication sessionAuth && sessionAuth.isAuthenticated()) {
                    SecurityContextHolder.getContext().setAuthentication(sessionAuth);
                    return sessionAuth;
                }
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户（密码登录时 principal 就是 User）
     */
    public static User getCurrentUser() {
        Authentication auth = getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        return null;
    }
}
