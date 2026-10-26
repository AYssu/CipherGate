package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.thirdparty.auth.ReplayProtectionService;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyAuthFilter;
import com.ayssu.ciphergate.thirdparty.crypto.CryptoRuntimeService;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyIntegrationRequest;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyIntegrationService;
import com.ayssu.ciphergate.thirdparty.web.ThirdPartyResponseEncryptionAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThirdPartyIntegrationProtocolTest {
    @Test
    void independentCredentialsReachBothActionsWithoutAppHeadersOrResponseEncryption() throws Exception {
        var apps = mock(ApplicationMapper.class);
        var crypto = mock(CryptoRuntimeService.class);
        var replay = mock(ReplayProtectionService.class);
        var filter = new ThirdPartyAuthFilter(apps, crypto, replay);
        var service = mock(ThirdPartyIntegrationService.class);
        var controller = new ThirdPartyIntegrationController(service);
        var json = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var advice = new ThirdPartyResponseEncryptionAdvice(crypto, json);
        for (String operation : new String[]{"trial", "authorization"}) {
            var action = operation.equals("trial") ? ThirdPartyIntegrationService.Action.TRIAL_APPLY
                    : ThirdPartyIntegrationService.Action.AUTH_QUERY;
            var http = new MockHttpServletRequest("POST", "/api/v1/third_party/" + operation);
            http.setRemoteAddr("203.0.113.7");
            http.addHeader("X-Forwarded-For", "198.51.100.99");
            http.setContentType("application/json");
            http.setContent("{\"apiKey\":\"independent_key\",\"appId\":10,\"userEmail\":\"user@example.com\",\"timestamp\":1,\"nonce\":\"test_nonce_123456\",\"sign\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
            var decoded = new AtomicReference<HttpServletRequest>();
            filter.doFilter(http, new MockHttpServletResponse(), (req, res) -> decoded.set((HttpServletRequest) req));
            assertNotNull(decoded.get());
            var body = json.readValue(decoded.get().getInputStream(), ThirdPartyIntegrationRequest.class);
            doReturn(Result.success(Map.of("appId", 10L, "expiresAt", "2027-01-01T00:00:00")))
                    .when(service).execute(eq(action), any(), eq("203.0.113.7"), isNull());
            var response = operation.equals("trial") ? controller.trial(body, decoded.get())
                    : controller.authorization(body, decoded.get());
            assertEquals(200, response.getStatusCode().value());
            assertTrue(json.writeValueAsString(response.getBody()).contains("expiresAt"));
            var method = new MethodParameter(ThirdPartyIntegrationController.class.getMethod(operation,
                    ThirdPartyIntegrationRequest.class, HttpServletRequest.class), -1);
            assertFalse(advice.supports(method, StringHttpMessageConverter.class));
            verify(service).execute(eq(action), eq(body), eq("203.0.113.7"), isNull());
        }
        verifyNoInteractions(apps, crypto, replay);
        var oldProtocol = new MockHttpServletRequest("POST", "/api/v1/user/login");
        var response = new MockHttpServletResponse();
        filter.doFilter(oldProtocol, response, (req, res) -> fail("Existing application authentication must remain required"));
        assertEquals(401, response.getStatus());
    }
}
