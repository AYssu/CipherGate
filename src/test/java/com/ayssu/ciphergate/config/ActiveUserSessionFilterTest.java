package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveUserSessionFilterTest {
    private UserService users;
    private ActiveUserSessionFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        users = mock(UserService.class);
        filter = new ActiveUserSessionFilter(users);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledPasswordUserInvalidatesExistingSession() throws Exception {
        loginPassword();
        User current = user("hash-before", "USER");
        current.setStatus(0);
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(current);
        assertRejected();
    }

    @Test
    void deletedPasswordUserInvalidatesExistingSession() throws Exception {
        loginPassword();
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(null);
        assertRejected();
    }

    @Test
    void resetPasswordInvalidatesExistingSession() throws Exception {
        loginPassword();
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(user("hash-after", "USER"));
        assertRejected();
    }

    @Test
    void enabledPasswordUserGetsFreshPrincipalAndRoles() throws Exception {
        loginPassword();
        User current = user("hash-before", "USER");
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(current);

        filter.doFilter(request, response, chain);

        var refreshed = SecurityContextHolder.getContext().getAuthentication();
        assertSame(current, refreshed.getPrincipal());
        assertSame(current, session.getAttribute("user"));
        assertSame(refreshed, session.getAttribute("passwordAuth"));
        assertEquals(List.of(new SimpleGrantedAuthority("ROLE_USER")), List.copyOf(refreshed.getAuthorities()));
        verify(chain).doFilter(request, response);

        // Refreshing the session must not mask a subsequent password change.
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(user("hash-after", "USER"));
        reset(chain);
        filter.doFilter(newRequestWithSession(), response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(session.isInvalid());
        verifyNoInteractions(chain);
    }

    @Test
    void oauthRolesAndBusinessSessionRefreshWithoutChangingRegistration() throws Exception {
        loginOAuth(Map.of("id", 42, "login", "example"));
        User current = user(null, "USER");
        when(users.getUserByGithubId("42")).thenReturn(current);
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(current);

        filter.doFilter(request, response, chain);

        OAuth2AuthenticationToken refreshed = (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertEquals("github", refreshed.getAuthorizedClientRegistrationId());
        assertEquals("example", refreshed.getName());
        assertEquals(Integer.valueOf(42), refreshed.getPrincipal().<Integer>getAttribute("id"));
        assertEquals(List.of(new SimpleGrantedAuthority("ROLE_USER")), List.copyOf(refreshed.getAuthorities()));
        assertSame(current, session.getAttribute("user"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledOauthUserInvalidatesSession() throws Exception {
        loginOAuth(Map.of("id", 42, "login", "example"));
        User current = user(null, "USER");
        current.setStatus(0);
        when(users.getUserByGithubId("42")).thenReturn(current);
        when(users.getUserWithRolesAndPermissions(1L)).thenReturn(current);
        assertRejected();
    }

    @Test
    void deletedOauthUserInvalidatesSession() throws Exception {
        loginOAuth(Map.of("id", 42, "login", "example"));
        assertRejected();
    }

    @Test
    void oauthPrincipalWithoutIdentityIsRejected() throws Exception {
        loginOAuth(Map.of("login", "example"));
        assertRejected();
        verifyNoInteractions(users);
    }

    @Test
    void anonymousRequestDoesNotCreateSessionOrQueryUsers() throws Exception {
        request = new MockHttpServletRequest();
        filter.doFilter(request, response, chain);
        assertNull(request.getSession(false));
        verifyNoInteractions(users);
        verify(chain).doFilter(request, response);
    }

    private void loginPassword() {
        User snapshot = user("hash-before", "SUPER_ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(snapshot, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        session.setAttribute("user", snapshot);
        session.setAttribute("passwordAuth", auth);
    }

    private void loginOAuth(Map<String, Object> attributes) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        var principal = new DefaultOAuth2User(authorities, attributes, "login");
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal, authorities, "github"));
        session.setAttribute("user", user(null, "SUPER_ADMIN"));
    }

    private void assertRejected() throws Exception {
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest newRequestWithSession() {
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setSession(session);
        return next;
    }

    private User user(String password, String roleCode) {
        User user = new User();
        user.setId(1L);
        user.setLogin("example");
        user.setStatus(1);
        user.setPassword(password);
        Role role = new Role();
        role.setRoleCode(roleCode);
        user.setRoles(List.of(role));
        return user;
    }
}
