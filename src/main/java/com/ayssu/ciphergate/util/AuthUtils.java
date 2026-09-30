package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
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
     * 获取当前登录用户，兼容密码登录和 GitHub OAuth2 登录。
     */
    public static User getCurrentUser() {
        Authentication auth = getAuthentication();
        if (auth == null) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        // OAuth2 principal 只包含 GitHub 资料，业务用户保存在当前 Session 中。
        // 校验 GitHub ID，避免读取到不属于当前认证身份的 Session 用户。
        if (principal instanceof OAuth2User oauth2User) {
            HttpSession session = getRequestSession();
            if (session == null) {
                return null;
            }

            Object sessionUser = session.getAttribute("user");
            if (!(sessionUser instanceof User user)) {
                return null;
            }

            Object githubId = oauth2User.getAttribute("id");
            return githubId != null && githubId.toString().equals(user.getGithubId()) ? user : null;
        }

        return null;
    }

    private static HttpSession getRequestSession() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getSession(false) : null;
    }
}
