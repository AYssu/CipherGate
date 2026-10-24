package com.ayssu.ciphergate.thirdparty.controller;

import cn.hutool.crypto.symmetric.AES;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.thirdparty.auth.*;
import com.ayssu.ciphergate.thirdparty.crypto.*;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.ayssu.ciphergate.thirdparty.service.*;
import com.ayssu.ciphergate.thirdparty.web.ThirdPartyResponseEncryptionAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HMAC/filter/AES/advice pipeline; database, replay storage and business service are mocked. */
@SuppressWarnings("unchecked")
class ThirdPartyUserProtocolTest {
    private static final String PATH = "/api/v1/user/login";
    private static final String KEY = "0123456789abcdef"; // Test fixture, never a production default.
    private static final String SECRET = "test-only-app-secret";
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AesDefaultCryptoPlugin aes = new AesDefaultCryptoPlugin();
    private CryptoRuntimeService crypto;
    private ReplayProtectionService replay;
    private ThirdPartyUserService service;
    private ThirdPartyUserController controller;
    private ThirdPartyAuthFilter filter;
    private ThirdPartyResponseEncryptionAdvice advice;

    @BeforeEach
    void setUp() {
        var apps = mock(ApplicationMapper.class);
        var app = new Application();
        app.setId(1L); app.setAppKey("test-app"); app.setAppSecret(SECRET); app.setStatus(1);
        app.setEncryptionPlugin("aes-default"); app.setEncryptionConfig(Map.of("aesKey", KEY));
        when(apps.selectOne(any())).thenReturn(app);
        replay = mock(ReplayProtectionService.class);
        when(replay.checkAndMark(anyString(), anyString(), anyLong(), anyLong())).thenReturn(true);
        crypto = mock(CryptoRuntimeService.class);
        when(crypto.decryptToMap(eq("aes-default"), anyMap())).thenAnswer(a -> aes.decryptToMap(a.getArgument(1)));
        when(crypto.encryptPayloadFromMap(eq("aes-default"), anyMap(), anyMap())).thenAnswer(a -> {
            Map<String, Object> plain = new LinkedHashMap<>(a.getArgument(1));
            plain.put("encryptionConfig", a.getArgument(2));
            return new CryptoEncryptedPayload("aes-default", aes.encryptFromMap(plain));
        });
        filter = new ThirdPartyAuthFilter(apps, crypto, replay);
        service = mock(ThirdPartyUserService.class);
        controller = new ThirdPartyUserController(service, mock(ThirdPartyUserRateLimitService.class));
        advice = new ThirdPartyResponseEncryptionAdvice(crypto, json);
    }

    @Test
    void encryptedJsonPreservesSpecialPasswordAndResponseIsEncryptedAndSigned() throws Exception {
        String password = "  test&=密码\"\\\n  ";
        var request = signedRequest(Map.of("username", "alice", "password", password, "deviceId", "device-1"));
        var result = new UserLoginResponse();
        result.setAppId(1L); result.setUserId(7L); result.setUsername("alice");
        result.setNickname("Alice&expiresAt=9999999999999&memberRequired=false");
        result.setOnline(true); result.setIdentityType("USER"); result.setIdentityId("user_7");
        result.setExpiresAt(LocalDateTime.of(2027, 1, 2, 3, 4, 5));
        result.setVariables("{\"tier\":\"member&name=Alice\"}");
        when(service.login(eq(1L), any(), any())).thenAnswer(a -> {
            UserLoginRequest body = a.getArgument(1);
            assertEquals(password, body.getPassword());
            assertEquals("alice", body.getUsername()); assertEquals("device-1", body.getDeviceId());
            return result;
        });
        AtomicReference<HttpServletRequest> decoded = new AtomicReference<>();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> decoded.set((HttpServletRequest) req));
        assertNotNull(decoded.get(), response.getContentAsString());
        var dto = json.readValue(decoded.get().getInputStream(), UserLoginRequest.class);
        var body = controller.login(dto, decoded.get());
        var method = new MethodParameter(ThirdPartyUserController.class.getMethod("login",
                UserLoginRequest.class, HttpServletRequest.class), -1);
        var httpResponse = new ServletServerHttpResponse(response);
        Object encrypted = advice.beforeBodyWrite(body, method, MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class, new ServletServerHttpRequest(decoded.get()), httpResponse);
        var envelope = (Result<ThirdPartyEncryptedData>) encrypted;
        assertEquals(200, envelope.getCode());
        assertEquals("aes-default", envelope.getData().getPluginId());
        var plain = aes.decryptToMap(Map.of("data", envelope.getData().getData(), "encryptionConfig", Map.of("aesKey", KEY)));
        assertEquals(7L, ((Number) plain.get("userId")).longValue()); assertEquals("alice", plain.get("username"));
        assertEquals(true, plain.get("online")); assertNull(plain.get("token"));
        assertEquals(result.getNickname(), plain.get("nickname"));
        assertFalse(plain.containsKey(AesDefaultCryptoPlugin.PLAINTEXT_FORMAT_KEY));
        assertFalse(plain.containsKey("encryptionConfig")); assertFalse(plain.containsKey("pluginConfig"));
        assertEquals("user_7", plain.get("identityId")); assertEquals(result.getVariables(), plain.get("variables"));
        assertEquals(result.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ((Number) plain.get("expiresAt")).longValue());
        assertFalse(plain.containsKey("password")); assertFalse(plain.containsKey("appSecret"));
        String wire = json.writeValueAsString(encrypted);
        assertFalse(wire.contains("alice")); assertFalse(wire.contains(SECRET)); assertFalse(wire.contains("user_7"));
        var headers = httpResponse.getHeaders();
        String digest = ThirdPartySignatureVerifier.sha256Hex(canonical(json.readTree(wire)).getBytes(StandardCharsets.UTF_8));
        String signature = ThirdPartySignatureVerifier.hmacSha256Hex(SECRET, ThirdPartySignatureVerifier.buildSignString(
                "POST", PATH, headers.getFirst(ThirdPartyHeaders.X_RESP_TIMESTAMP),
                headers.getFirst(ThirdPartyHeaders.X_RESP_NONCE), digest));
        assertEquals(signature, headers.getFirst(ThirdPartyHeaders.X_RESP_SIGNATURE));
        verify(service).login(eq(1L), any(), any());
    }

    @Test
    void missingBadExpiredAndReplayedSignaturesNeverReachLogin() throws Exception {
        for (String scenario : List.of("missing", "bad", "expired", "replay")) {
            when(replay.checkAndMark(anyString(), anyString(), anyLong(), anyLong())).thenReturn(!scenario.equals("replay"));
            var request = signedRequest(Map.of("username", "alice", "password", "secret", "deviceId", "device-1"));
            switch (scenario) {
                case "missing" -> request.removeHeader(ThirdPartyHeaders.X_SIGNATURE);
                case "bad" -> {
                    request.removeHeader(ThirdPartyHeaders.X_SIGNATURE);
                    request.addHeader(ThirdPartyHeaders.X_SIGNATURE, "0000");
                }
                case "expired" -> {
                    request.removeHeader(ThirdPartyHeaders.X_TIMESTAMP);
                    request.addHeader(ThirdPartyHeaders.X_TIMESTAMP, "1");
                }
                default -> { }
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("Unauthenticated request reached business code: " + scenario));
            assertEquals(401, response.getStatus(), scenario);
        }
        verifyNoInteractions(service, crypto);
    }

    @Test
    void configuredNonDefaultPluginIsNotForcedIntoAesJsonFormat() throws Exception {
        var request = new MockHttpServletRequest("POST", PATH);
        request.setAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_PLUGIN_ID, "custom-plugin");
        request.setAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_CONFIG, Map.of("custom", "test"));
        when(crypto.encryptPayloadFromMap(eq("custom-plugin"), anyMap(), anyMap())).thenAnswer(a -> {
            Map<String, Object> plain = a.getArgument(1);
            assertFalse(plain.containsKey(AesDefaultCryptoPlugin.PLAINTEXT_FORMAT_KEY));
            assertEquals(7L, ((Number) plain.get("userId")).longValue());
            return new CryptoEncryptedPayload("custom-plugin", "opaque-test-data");
        });
        var result = new UserLoginResponse(); result.setUserId(7L);
        var method = new MethodParameter(ThirdPartyUserController.class.getMethod("login",
                UserLoginRequest.class, HttpServletRequest.class), -1);
        var output = (Result<ThirdPartyEncryptedData>) advice.beforeBodyWrite(Result.success(result), method,
                MediaType.APPLICATION_JSON, StringHttpMessageConverter.class, new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()));
        assertEquals("custom-plugin", output.getData().getPluginId());
        assertEquals("opaque-test-data", output.getData().getData());
    }

    @Test
    void oldCanonicalCardPayloadRemainsCompatible() {
        String cipher = new AES(KEY.getBytes(StandardCharsets.UTF_8)).encryptHex("cardCode=ABC123&deviceId=dev-1");
        assertEquals(cipher, aes.encryptFromMap(Map.of("cardCode", "ABC123", "deviceId", "dev-1",
                "encryptionConfig", Map.of("aesKey", KEY))));
        assertEquals(Map.of("cardCode", "ABC123", "deviceId", "dev-1"), aes.decryptToMap(
                Map.of("data", cipher, "encryptionConfig", Map.of("aesKey", KEY))));
    }

    private MockHttpServletRequest signedRequest(Map<String, Object> plain) throws Exception {
        String cipher = new AES(KEY.getBytes(StandardCharsets.UTF_8)).encryptHex(json.writeValueAsString(plain));
        byte[] body = json.writeValueAsBytes(Map.of("data", cipher));
        String ts = String.valueOf(System.currentTimeMillis()), nonce = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest("POST", PATH);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE); request.setContent(body);
        request.addHeader(ThirdPartyHeaders.X_APP_KEY, "test-app"); request.addHeader(ThirdPartyHeaders.X_TIMESTAMP, ts);
        request.addHeader(ThirdPartyHeaders.X_NONCE, nonce);
        request.addHeader(ThirdPartyHeaders.X_SIGNATURE, ThirdPartySignatureVerifier.hmacSha256Hex(SECRET,
                ThirdPartySignatureVerifier.buildSignString("POST", PATH, ts, nonce, ThirdPartySignatureVerifier.sha256Hex(body))));
        return request;
    }

    private String canonical(JsonNode node) {
        if (node.isNull()) return "null";
        if (node.isObject()) {
            List<String> fields = new ArrayList<>(); node.fieldNames().forEachRemaining(fields::add); Collections.sort(fields);
            return String.join("&", fields.stream().map(k -> k + "=" + canonical(node.get(k))).toList());
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>(); node.forEach(v -> values.add(canonical(v)));
            return "[" + String.join(",", values) + "]";
        }
        return node.asText();
    }
}
