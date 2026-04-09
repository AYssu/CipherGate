package com.ayssu.ciphergate.thirdparty.web;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.crypto.CryptoRuntimeService;
import com.ayssu.ciphergate.thirdparty.crypto.CryptoEncryptedPayload;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyEncryptedData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@ControllerAdvice(basePackages = "com.ayssu.ciphergate.thirdparty.controller")
@RequiredArgsConstructor
public class ThirdPartyResponseEncryptionAdvice implements ResponseBodyAdvice<Object> {

    private final CryptoRuntimeService cryptoRuntimeService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(@NonNull MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (!(body instanceof Result<?> r)) {
            return body;
        }
        if (r.getCode() == null || r.getCode() != 200) {
            return body;
        }
        if (r.getData() == null) {
            return body;
        }

        String pluginId = null;
        String appSecret = null;
        Map<String, Object> encryptionConfig = null;
        if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletReq) {
            pluginId = (String) servletReq.getServletRequest().getAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_PLUGIN_ID);
            appSecret = (String) servletReq.getServletRequest().getAttribute(ThirdPartyHeaders.ATTR_APP_SECRET);
            Object cfg = servletReq.getServletRequest().getAttribute(ThirdPartyHeaders.ATTR_ENCRYPTION_CONFIG);
            if (cfg instanceof Map<?, ?> m) {
                encryptionConfig = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    encryptionConfig.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }

        Map<String, Object> plainMap = objectMapper.convertValue(r.getData(), new TypeReference<>() {});
        if (plainMap == null) {
            plainMap = new LinkedHashMap<>();
        }
        plainMap = normalizeTemporalFields(plainMap);

        CryptoEncryptedPayload payload = cryptoRuntimeService.encryptPayloadFromMap(pluginId, plainMap, encryptionConfig);
        ThirdPartyEncryptedData encryptedData = new ThirdPartyEncryptedData();
        encryptedData.setData(payload.data());
        encryptedData.setPluginId(payload.pluginId());

        Result<ThirdPartyEncryptedData> out = new Result<>();
        out.setCode(r.getCode());
        out.setMessage(r.getMessage());
        out.setSuccess(r.getSuccess());
        out.setTimestamp(r.getTimestamp());
        out.setData(encryptedData);

        // Add response signature headers so client can verify response integrity.
        // NOTE: Sign a canonical form built from the JSON structure (object keys sorted),
        // so client/server stay consistent across property order and different response DTOs.
        if (appSecret != null && !appSecret.isBlank()) {
            String respTs = String.valueOf(Instant.now().toEpochMilli());
            String respNonce = UUID.randomUUID().toString().replace("-", "");
            try {
                String respBodyJson = objectMapper.writeValueAsString(out);
                JsonNode tree = objectMapper.readTree(respBodyJson);
                String canonical = canonicalize(tree);
                String bodyDigest = ThirdPartySignatureVerifier.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
                String method = request.getMethod() == null ? "POST" : request.getMethod().name();
                String path = request.getURI() == null ? "" : request.getURI().getPath();
                String signString = ThirdPartySignatureVerifier.buildSignString(method, path, respTs, respNonce, bodyDigest);
                String sig = ThirdPartySignatureVerifier.hmacSha256Hex(appSecret, signString);
                response.getHeaders().set(ThirdPartyHeaders.X_RESP_TIMESTAMP, respTs);
                response.getHeaders().set(ThirdPartyHeaders.X_RESP_NONCE, respNonce);
                response.getHeaders().set(ThirdPartyHeaders.X_RESP_SIGNATURE, sig);
            } catch (Exception e) {
                log.warn("Failed to sign third-party response", e);
            }
        }
        return out;
    }

    private String canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "null";
        }
        if (node.isObject()) {
            TreeSet<String> keys = new TreeSet<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String k : keys) {
                if (!first) sb.append("&");
                first = false;
                sb.append(k).append("=").append(canonicalize(node.get(k)));
            }
            return sb.toString();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(canonicalize(node.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue().toString();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }
        // fallback for other scalar node types
        return node.asText();
    }

    private Map<String, Object> normalizeTemporalFields(Map<String, Object> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            out.put(e.getKey(), normalizeValue(e.getKey(), e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> me : m.entrySet()) {
                nested.put(String.valueOf(me.getKey()), normalizeValue(String.valueOf(me.getKey()), me.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            // If this looks like Jackson's LocalDateTime timestamp array: [yyyy, MM, dd, HH, mm, ss, ...]
            if (key != null && key.endsWith("At") && list.size() >= 6 && list.get(0) instanceof Number) {
                try {
                    int year = ((Number) list.get(0)).intValue();
                    int month = ((Number) list.get(1)).intValue();
                    int day = ((Number) list.get(2)).intValue();
                    int hour = ((Number) list.get(3)).intValue();
                    int minute = ((Number) list.get(4)).intValue();
                    int second = ((Number) list.get(5)).intValue();
                    int nano = list.size() >= 7 && list.get(6) instanceof Number ? ((Number) list.get(6)).intValue() : 0;
                    LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second, nano);
                    return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                } catch (Exception ignored) {
                    // fall through to generic list handling
                }
            }
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object v : list) {
                normalized.add(normalizeValue(key, v));
            }
            return normalized;
        }
        return value;
    }
}

