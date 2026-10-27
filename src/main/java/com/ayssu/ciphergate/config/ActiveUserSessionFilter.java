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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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
                User current = userService.getUserWithRolesAndPermissions(loginUser.getId());
                disabled = current == null || !Integer.valueOf(1).equals(current.getStatus())
                        || !Objects.equals(loginUser.getPassword(), current.getPassword());
                if (!disabled) {
                    var refreshed = new UsernamePasswordAuthenticationToken(current, null, authoritiesFor(current));
                    refreshed.setDetails(authentication.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(refreshed);
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.setAttribute("user", current);
                        session.setAttribute("passwordAuth", refreshed);
                    }
                }
            }
            // OAuth2 登录：principal 是 OAuth2User，通过 githubId 查库校验
            else if (principal instanceof OAuth2User oauth2User) {
                Object idObj = oauth2User.getAttribute("id");
                String githubId = idObj == null ? null : idObj.toString();
                User user = githubId == null ? null : userService.getUserByGithubId(githubId);
                User current = user == null ? null : userService.getUserWithRolesAndPermissions(user.getId());
                disabled = current == null || !Integer.valueOf(1).equals(current.getStatus());
                if (!disabled && authentication instanceof OAuth2AuthenticationToken oauthToken) {
                    var authorities = authoritiesFor(current);
                    var refreshedPrincipal = new DefaultOAuth2User(authorities, oauth2User.getAttributes(), "login");
                    var refreshed = new OAuth2AuthenticationToken(refreshedPrincipal, authorities,
                            oauthToken.getAuthorizedClientRegistrationId());
                    refreshed.setDetails(authentication.getDetails());
                    SecurityContextHolder.getContext().setAuthentication(refreshed);
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.setAttribute("user", current);
                    }
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
                response.getWriter().write("{\"code\":403,\"message\":\"账号状态或登录凭证已变更，请重新登录\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> authoritiesFor(User user) {
        return user.getRoles() == null ? List.of() : user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode())).toList();
    }
}
