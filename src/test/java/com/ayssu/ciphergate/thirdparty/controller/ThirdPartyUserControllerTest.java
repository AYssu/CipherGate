package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginRequest;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginResponse;
import com.ayssu.ciphergate.thirdparty.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThirdPartyUserControllerTest {
    private ThirdPartyUserService service;
    private ThirdPartyUserRateLimitService limiter;
    private ThirdPartyUserController controller;
    private MockHttpServletRequest http;
    private UserLoginRequest request;

    @BeforeEach
    void setUp() {
        service = mock(ThirdPartyUserService.class); limiter = mock(ThirdPartyUserRateLimitService.class);
        controller = new ThirdPartyUserController(service, limiter);
        http = new MockHttpServletRequest(); http.setRemoteAddr("192.0.2.1");
        request = new UserLoginRequest(); request.setUsername("alice");
    }

    @Test
    void missingApplicationCannotInvokeService() {
        assertEquals(401, controller.login(request, http).getCode());
        verifyNoInteractions(service, limiter);
    }

    @Test
    void rateLimitStopsServiceAndDoesNotExposePassword() {
        http.setAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID, 1L);
        doThrow(new ThirdPartyUserLoginException(429, "请求过于频繁"))
                .when(limiter).checkBeforeLogin(1L, "192.0.2.1", "alice");
        assertEquals(429, controller.login(request, http).getCode());
        verifyNoInteractions(service);
    }

    @Test
    void returnsUserResponseOnlyAfterLimiterAndService() {
        http.setAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID, 1L);
        UserLoginResponse data = new UserLoginResponse(); data.setUserId(7L);
        when(service.login(1L, request, "192.0.2.1")).thenReturn(data);
        var result = controller.login(request, http);
        assertTrue(result.getSuccess()); assertSame(data, result.getData());
        var order = inOrder(limiter, service);
        order.verify(limiter).checkBeforeLogin(1L, "192.0.2.1", "alice");
        order.verify(service).login(1L, request, "192.0.2.1");
    }

    @Test
    void unexpectedErrorsAreSanitized() {
        http.setAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID, 1L);
        when(service.login(1L, request, "192.0.2.1")).thenThrow(new IllegalStateException("SQL secret test-only"));
        var result = controller.login(request, http);
        assertEquals(500, result.getCode()); assertFalse(result.getMessage().contains("secret"));
    }
}
