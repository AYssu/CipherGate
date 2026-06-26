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

                    Map<String, Object> principal = new HashMap<>();
                    principal.put("id", appUserId);
                    principal.put("appId", appId);
                    principal.put("email", email);
                    principal.put("nickname", nickname);

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            principal, null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_PORTAL_USER"))
                        );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.warn("Portal JWT parse error: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
