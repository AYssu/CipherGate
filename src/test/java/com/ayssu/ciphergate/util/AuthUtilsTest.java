package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.User;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsPasswordLoginUser() {
        User user = user(11L, "github-id");
        setRequest(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        assertEquals(user, AuthUtils.getCurrentUser());
    }

    @Test
    void returnsOAuthSessionUserForMatchingGitHubId() {
        User user = user(11L, "github-id");
        setRequest(user);
        setOAuthAuthentication("github-id");

        assertEquals(user, AuthUtils.getCurrentUser());
    }

    @Test
    void rejectsOAuthSessionUserForDifferentGitHubId() {
        setRequest(user(11L, "another-github-id"));
        setOAuthAuthentication("github-id");

        assertNull(AuthUtils.getCurrentUser());
    }

    @Test
    void doesNotReturnSessionUserWithoutAuthentication() {
        setRequest(user(11L, "github-id"));

        assertNull(AuthUtils.getCurrentUser());
    }

    private void setRequest(User sessionUser) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession();
        session.setAttribute("user", sessionUser);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void setOAuthAuthentication(String githubId) {
        OAuth2User principal = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of("id", githubId, "login", "test-user"),
                "login"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private User user(Long id, String githubId) {
        User user = new User();
        user.setId(id);
        user.setGithubId(githubId);
        user.setLogin("test-user");
        return user;
    }
}
