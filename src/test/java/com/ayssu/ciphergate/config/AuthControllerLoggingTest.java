package com.ayssu.ciphergate.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ayssu.ciphergate.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerLoggingTest {
    @Test void neitherPrincipalNorSessionFallbackLogsOAuthAttributes() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthController.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start(); logger.addAppender(capture);
        try {
            var attributes = Map.<String,Object>of("id", 7, "login", "test-user",
                    "email", "private-identity-marker@example.test", "access_token", "synthetic-token-marker");
            var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "login");
            var session = new MockHttpSession(); session.setAttribute("githubUser", attributes);
            var controller = new AuthController();
            assertNotNull(controller.user(principal, session));
            assertNotNull(controller.user(null, session));
            String logs = capture.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertFalse(logs.contains("private-identity-marker"));
            assertFalse(logs.contains("synthetic-token-marker"));
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
}
