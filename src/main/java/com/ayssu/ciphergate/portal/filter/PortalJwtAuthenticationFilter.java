package com.ayssu.ciphergate.portal.filter;

import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalJwtAuthenticationFilter extends OncePerRequestFilter {

    private final PortalJwtUtil jwtUtil;
    private final com.ayssu.ciphergate.mapper.AppUserMapper users;
    private final com.ayssu.ciphergate.mapper.ApplicationMapper applications;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The bean can also be registered as a servlet filter by Boot. Portal credentials
        // must never authenticate administrator or third-party API requests.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/portal/") || path.startsWith("/api/portal/admin/")
                || path.equals("/api/portal/admin") || path.startsWith("/api/portal/epay-config/")
                || path.equals("/api/portal/epay-config");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.validateToken(token)) {
                try {
                    Claims claims = jwtUtil.parseToken(token);
                    Long appUserId = Long.parseLong(claims.getSubject());
                    Long appId = claims.get("appId", Long.class);
                    String email = claims.get("email", String.class);
                    String nickname = claims.get("nickname", String.class);

                    boolean selection = "SELECT_APP".equals(claims.get("purpose"));
                    if (!selection) {
                        var user = users.selectById(appUserId);
                        var app = appId == null ? null : applications.selectById(appId);
                        if (!"SESSION".equals(claims.get("purpose")) || user == null || appId == null || appId <= 0
                                || Integer.valueOf(1).equals(user.getDeleted()) || (app != null && Integer.valueOf(1).equals(app.getDeleted()))
                                || !appId.equals(user.getAppId()) || app == null || !Integer.valueOf(1).equals(app.getStatus())
                                || !jwtUtil.credentialStamp(user).equals(claims.get("credential", String.class))) {
                            throw new IllegalArgumentException("Portal identity is no longer active");
                        }
                    }
                    Map<String, Object> principal = new HashMap<>();
                    principal.put("id", appUserId);
                    principal.put("appId", appId);
                    principal.put("email", email);
                    principal.put("nickname", nickname);
                    principal.put("grants", claims.get("grants"));
                    principal.put("purpose", claims.get("purpose"));

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            principal, null,
                            Collections.singletonList(new SimpleGrantedAuthority(selection ? "ROLE_PORTAL_SELECT" : "ROLE_PORTAL_USER"))
                        );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.debug("Portal JWT authentication rejected");
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
