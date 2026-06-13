package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每次请求校验当前登录用户是否仍处于启用状态。
 * 同时负责从 Session 恢复密码登录用户的 SecurityContext（绕过 JDBC 序列化问题）。
 */
@Component
@RequiredArgsConstructor
public class ActiveUserSessionFilter extends OncePerRequestFilter {

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 如果 SecurityContext 中没有认证信息，尝试从 Session 恢复（密码登录场景）
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof PreAuthenticatedAuthenticationToken) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object passwordAuth = session.getAttribute("passwordAuth");
                if (passwordAuth instanceof Authentication sessionAuth && sessionAuth.isAuthenticated()) {
                    SecurityContextHolder.getContext().setAuthentication(sessionAuth);
                    authentication = sessionAuth;
                }
            }
        }

        // 校验用户是否被禁用
        if (authentication != null && authentication.isAuthenticated()) {
            boolean disabled = false;
            Object principal = authentication.getPrincipal();

            // 密码登录：principal 是 User 对象
            if (principal instanceof User loginUser) {
                disabled = loginUser.getStatus() == null || loginUser.getStatus() != 1;
            }
            // OAuth2 登录：principal 是 OAuth2User，通过 githubId 查库校验
            else if (principal instanceof OAuth2User oauth2User) {
                Object idObj = oauth2User.getAttribute("id");
                String githubId = idObj == null ? null : idObj.toString();
                if (githubId != null) {
                    User user = userService.getUserByGithubId(githubId);
                    disabled = user == null || user.getStatus() == null || user.getStatus() != 1;
                }
            }

            if (disabled) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"账号已被禁用\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
