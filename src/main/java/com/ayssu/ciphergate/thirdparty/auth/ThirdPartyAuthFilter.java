package com.ayssu.ciphergate.thirdparty.auth;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.thirdparty.crypto.CryptoRuntimeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyAuthFilter extends OncePerRequestFilter {
    private static final long MAX_SKEW_MS = 15_000L;

    private final ApplicationMapper applicationMapper;
    private final CryptoRuntimeService cryptoRuntimeService;
    private final ReplayProtectionService replayProtectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        // WebSocket endpoint uses its own HELLO/AUTH inside WS frames.
        if (path.startsWith("/api/v1/ws")) {
            return true;
        }
        // Also skip WebSocket upgrade requests (defensive).
        String upgrade = request.getHeader("Upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String appKey = trimToNull(request.getHeader(ThirdPartyHeaders.X_APP_KEY));
            String ts = trimToNull(request.getHeader(ThirdPartyHeaders.X_TIMESTAMP));
            String nonce = trimToNull(request.getHeader(ThirdPartyHeaders.X_NONCE));
            String signature = trimToNull(request.getHeader(ThirdPartyHeaders.X_SIGNATURE));

            if (!StringUtils.hasText(appKey) || !StringUtils.hasText(ts) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
                writeError(response, 401, "THIRD_PARTY_AUTH_MISSING", "缺少必要鉴权头");
                return;
            }

            long timestampMs;
            try {
                timestampMs = Long.parseLong(ts);
            } catch (Exception e) {
                writeError(response, 401, "THIRD_PARTY_AUTH_BAD_TIMESTAMP", "时间戳格式错误");
                return;
            }
            long nowMs = Instant.now().toEpochMilli();
            if (Math.abs(nowMs - timestampMs) > MAX_SKEW_MS) {
                writeError(response, 401, "THIRD_PARTY_AUTH_EXPIRED", "请求已过期");
                return;
            }

            Application app = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                    .eq(Application::getAppKey, appKey)
                    .eq(Application::getDeleted, 0)
                    .last("limit 1"));
            if (app == null) {
                writeError(response, 401, "THIRD_PARTY_AUTH_APP_NOT_FOUND", "应用不存在");
                return;
            }
            if (app.getStatus() != null && app.getStatus() != 1) {
                writeError(response, 403, "APP_DISABLED", "应用不可用");
                return;
            }

            if (!replayProtectionService.checkAndMark(appKey, nonce, timestampMs, nowMs)) {
                writeError(response, 401, "THIRD_PARTY_AUTH_REPLAY", "请求重复或时间戳非法");
                return;
            }

            byte[] rawBytes = request.getInputStream().readAllBytes();
            String rawBody = new String(rawBytes, StandardCharsets.UTF_8);
            Map<String, Object> rawMap = parseBodyMap(rawBody);
            String data = rawMap.get("data") instanceof String s ? s : null;
            String canonical = buildCanonical(rawMap);

            // 1) Verify signature against the original raw request body to prove body is not tampered.
            String bodyDigest = ThirdPartySignatureVerifier.sha256Hex(rawBody.getBytes(StandardCharsets.UTF_8));
            String signString = ThirdPartySignatureVerifier.buildSignString(
                    request.getMethod(),
                    request.getRequestURI(),
                    ts,
                    nonce,
                    bodyDigest
            );
            String expected = ThirdPartySignatureVerifier.hmacSha256Hex(app.getAppSecret(), signString);
            if (!expected.equalsIgnoreCase(signature)) {
                writeError(response, 401, "THIRD_PARTY_AUTH_BAD_SIGNATURE", "签名错误");
                return;
            }

            Map<String, Object> pluginInput = new HashMap<>();
            pluginInput.put("headers", extractHeaders(request));
            pluginInput.put("rawBody", rawBody);
            pluginInput.put("bodyMap", rawMap);
            pluginInput.put("data", data);
            pluginInput.put("canonical", canonical);
            pluginInput.put("appId", app.getId());
            pluginInput.put("appKey", app.getAppKey());
            pluginInput.put("encryptionConfig", app.getEncryptionConfig());

            Map<String, Object> decryptedMap = cryptoRuntimeService.decryptToMap(app.getEncryptionPlugin(), pluginInput);
            if (decryptedMap == null || decryptedMap.isEmpty()) {
                writeError(response, 400, "DECRYPT_EMPTY", "解密报文为空");
                return;
            }

            // 2) Cross-check: ensure decrypted data matches the same fields in the original body.
            // This proves `data` and plaintext body fields are consistent.
            for (Map.Entry<String, Object> e : decryptedMap.entrySet()) {
                String k = e.getKey();
                if (!StringUtils.hasText(k) || "data".equals(k)) {
                    continue;
                }
                if (!rawMap.containsKey(k)) {
                    continue;
                }
                String rawVal = String.valueOf(rawMap.get(k));
                String decVal = String.valueOf(e.getValue());
                if (!rawVal.equals(decVal)) {
                    writeError(response, 400, "DECRYPT_MISMATCH", "解密字段不一致: " + k);
                    return;
                }
            }
            String decryptedBody = objectMapper.writeValueAsString(decryptedMap);

            request.setAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID, app.getId());
            request.setAttribute(ThirdPartyHeaders.ATTR_APP_KEY, app.getAppKey());
            request.setAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_PLUGIN_ID, app.getEncryptionPlugin());
            request.setAttribute(ThirdPartyHeaders.ATTR_APP_SECRET, app.getAppSecret());
            request.setAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_CONFIG, app.getEncryptionConfig());

            byte[] replaced = decryptedBody.getBytes(StandardCharsets.UTF_8);
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, replaced);
            filterChain.doFilter(wrapped, response);
        } catch (Exception e) {
            log.error("ThirdPartyAuthFilter failed", e);
            writeError(response, 500, "THIRD_PARTY_AUTH_ERROR", "鉴权失败");
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new HashMap<>();
        body.put("code", httpStatus);
        body.put("bizCode", code);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBodyMap(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(rawBody, Map.class);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String buildCanonical(Map<String, Object> bodyMap) {
        if (bodyMap == null || bodyMap.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>(bodyMap.keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            Object v = bodyMap.get(k);
            if (v == null) {
                continue;
            }
            sb.append(k).append("=").append(v).append("&");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}

