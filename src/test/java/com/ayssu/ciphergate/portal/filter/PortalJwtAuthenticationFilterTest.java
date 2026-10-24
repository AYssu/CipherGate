package com.ayssu.ciphergate.portal.filter;

import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PortalJwtAuthenticationFilterTest {
    private final PortalJwtUtil jwt = new PortalJwtUtil("ciphergate-unit-test-secret-for-jwt-only-123456789", 60_000);
    private final PortalJwtAuthenticationFilter filter = new PortalJwtAuthenticationFilter(jwt);

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void validPortalBearerSetsOnlyPortalRoleAndCorrectAppIdentity() throws Exception {
        var request = request("/api/portal/dashboard", jwt.generateToken(7L, 3L, "m@example.test", "M"));
        var invoked = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> invoked.set(true));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(invoked.get());
        assertTrue(authentication.isAuthenticated());
        assertEquals(7L, ((Map<?, ?>) authentication.getPrincipal()).get("id"));
        assertEquals(3L, ((Map<?, ?>) authentication.getPrincipal()).get("appId"));
        assertEquals(java.util.List.of("ROLE_PORTAL_USER"), authentication.getAuthorities().stream()
                .map(Object::toString).toList());
    }

    @Test void invalidAndMissingCredentialsDoNotCreateAuthentication() throws Exception {
        for (String token : new String[]{null, "invalid", ""}) {
            filter.doFilter(request("/api/portal/dashboard", token), new MockHttpServletResponse(), (req, res) -> {});
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test void portalTokenCannotAuthenticateAdministratorOrThirdPartyEndpoints() throws Exception {
        for (String path : new String[]{"/api/user/info", "/api/applications", "/api/v1/third_party/recharge",
                "/api/portal-evil", "/api/portal/admin/orders", "/api/portal/epay-config/1"}) {
            filter.doFilter(request(path, jwt.generateToken(7L, 3L, "m@example.test", "M")),
                    new MockHttpServletResponse(), (req, res) -> {});
            assertNull(SecurityContextHolder.getContext().getAuthentication(), path);
        }
    }

    @Test void contextPathDoesNotBypassPortalBoundary() throws Exception {
        var request = request("/ciphergate/api/user/info", jwt.generateToken(7L, 3L, "m@example.test", "M"));
        request.setContextPath("/ciphergate");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String path, String token) {
        var request = new MockHttpServletRequest("GET", path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
