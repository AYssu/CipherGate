package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.dto.PasswordLoginRequest;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.ActivityLogService;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordLoginControllerSecurityTest {
    private static final String REAL_PASSWORD = new BCryptPasswordEncoder().encode("correct-password");
    UserService users;
    ActivityLogService logs;
    LoginRateLimiter limiter;
    PublicAbuseGuard guard;
    PasswordLoginController controller;
    HttpServletRequest http;
    HttpServletResponse response;
    PasswordLoginRequest request;
    @BeforeEach void setup() {
        users = mock(UserService.class); logs = mock(ActivityLogService.class);
        limiter = mock(LoginRateLimiter.class); guard = mock(PublicAbuseGuard.class);
        controller = new PasswordLoginController(users, logs, limiter, guard);
        http = mock(HttpServletRequest.class); response = mock(HttpServletResponse.class);
        when(http.getRemoteAddr()).thenReturn("192.0.2.4");
        request = new PasswordLoginRequest(); request.setLogin("  Member  "); request.setPassword("wrong-password");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"missing", "null-password", "blank-password", "invalid-hash", "wrong-password"})
    void allRejectedCredentialsAreUniformCountBothLimitsAndNeverCreateSession(String scenario) throws Exception {
        User user = new User(); user.setId(7L); user.setStatus(1); user.setPassword(REAL_PASSWORD);
        switch (scenario) {
            case "missing" -> user = null;
            case "null-password" -> { user.setPassword(null); request.setPassword("unavailable-admin-account"); }
            case "blank-password" -> user.setPassword("");
            case "invalid-hash" -> user.setPassword("not-a-bcrypt-hash");
            default -> { }
        }
        when(users.findByLogin("member")).thenReturn(user);
        var result = controller.login(request, http, response);
        assertEquals(401, result.getCode()); assertEquals("用户名或密码错误", result.getMessage());
        assertNull(result.getData()); assertFalse(result.getSuccess());
        verify(limiter).recordFailure("account:member"); verify(limiter).recordFailure("ip:192.0.2.4");
        verify(limiter, never()).clearFailures(anyString());
        verify(http, never()).getSession(anyBoolean()); verify(http, never()).changeSessionId();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(users, never()).getUserWithRolesAndPermissions(anyLong()); verifyNoInteractions(logs);
    }

    @Test void accountLockRejectsBeforeCredentialLookup() throws Exception {
        when(limiter.getLockRemainingSeconds("account:member")).thenReturn(120L);
        assertEquals(429, controller.login(request, http, response).getCode());
        verifyNoInteractions(users, logs); verify(limiter, never()).recordFailure(anyString());
        verify(http, never()).getSession(anyBoolean());
    }

    @Test void ipLockRejectsBeforeCredentialLookup() throws Exception {
        when(limiter.getLockRemainingSeconds("ip:192.0.2.4")).thenReturn(120L);
        assertEquals(429, controller.login(request, http, response).getCode());
        verifyNoInteractions(users, logs); verify(limiter, never()).recordFailure(anyString());
        verify(http, never()).getSession(anyBoolean());
    }

    @Test void requestLimitFailsClosedBeforeAnyAccountWork() {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(guard).check(anyString(), anyString(), anyLong(), any());
        var failure = assertThrows(ResponseStatusException.class, () -> controller.login(request, http, response));
        assertEquals(429, failure.getStatusCode().value());
        verifyNoInteractions(users, logs, limiter); verify(http, never()).getSession(anyBoolean());
    }
}
