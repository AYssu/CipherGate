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
    private final com.ayssu.ciphergate.mapper.AppUserMapper users = org.mockito.Mockito.mock(com.ayssu.ciphergate.mapper.AppUserMapper.class);
    private final com.ayssu.ciphergate.mapper.ApplicationMapper applications = org.mockito.Mockito.mock(com.ayssu.ciphergate.mapper.ApplicationMapper.class);
    private final PortalJwtAuthenticationFilter filter = new PortalJwtAuthenticationFilter(jwt, users, applications);
    private com.ayssu.ciphergate.entity.AppUser user() {
        var u = new com.ayssu.ciphergate.entity.AppUser(); u.setId(7L); u.setAppId(3L); u.setEmail("m@example.test"); u.setPassword("stored-hash");
        org.mockito.Mockito.when(users.selectById(7L)).thenReturn(u);
        var app = new com.ayssu.ciphergate.entity.Application(); app.setId(3L); app.setStatus(1);
        org.mockito.Mockito.when(applications.selectById(3L)).thenReturn(app);
        return u;
    }

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void validPortalBearerSetsOnlyPortalRoleAndCorrectAppIdentity() throws Exception {
        var request = request("/api/portal/dashboard", jwt.generateSessionToken(user()));
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
            filter.doFilter(request(path, jwt.generateSessionToken(user())),
                    new MockHttpServletResponse(), (req, res) -> {});
            assertNull(SecurityContextHolder.getContext().getAuthentication(), path);
        }
    }

    @Test void contextPathDoesNotBypassPortalBoundary() throws Exception {
        var request = request("/ciphergate/api/user/info", jwt.generateSessionToken(user()));
        request.setContextPath("/ciphergate");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void temporaryCredentialOnlyGrantsSelectionRole() throws Exception {
        filter.doFilter(request("/api/portal/settings/profile", jwt.generateSelectionToken("m@example.test", java.util.List.of(user()))), new MockHttpServletResponse(), (req, res) -> {});
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(java.util.List.of("ROLE_PORTAL_SELECT"), auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test void changedPasswordDeletedUserAndDisabledApplicationRejectSession() throws Exception {
        var u = user(); String token = jwt.generateSessionToken(u);
        u.setPassword("new-password-hash");
        filter.doFilter(request("/api/portal/settings/profile", token), new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        org.mockito.Mockito.when(users.selectById(7L)).thenReturn(null);
        filter.doFilter(request("/api/portal/settings/profile", token), new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        u = user(); token = jwt.generateSessionToken(u);
        var app = new com.ayssu.ciphergate.entity.Application(); app.setStatus(0);
        org.mockito.Mockito.when(applications.selectById(3L)).thenReturn(app);
        filter.doFilter(request("/api/portal/settings/profile", token), new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void oldUnscopedTokensCannotAuthenticate() throws Exception {
        user();
        filter.doFilter(request("/api/portal/settings/profile", jwt.generateToken(7L, 3L, "m@example.test", "M")), new MockHttpServletResponse(), (req, res) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String path, String token) {
        var request = new MockHttpServletRequest("GET", path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
    @Test void rejectedIdentityInvokesDownstreamExactlyOnceAndPropagatesItsError() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(
            request("/api/portal/settings/profile", jwt.generateToken(7L,3L,"m@example.test","M")),
            new MockHttpServletResponse(), (req,res) -> { calls.incrementAndGet(); throw new jakarta.servlet.ServletException("downstream failed"); }));
        assertEquals(1,calls.get());
    }
}
