package com.ayssu.ciphergate.thirdparty.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Local fallback implementation: treat request body as already decrypted JSON.
 * Your RSA plugin can override this via PF4J and matching pluginId.
 */
@Component
public class DefaultPassthroughCryptoPlugin implements CryptoPluginEncryptor {
    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String pluginId() {
        return "rsa-default";
    }

    @Override
    public Map<String, Object> decryptToMap(Map<String, Object> input) {
        Object data = input.get("data");
        if (data instanceof String s && !s.isBlank()) {
            try {
                return OM.readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }
        Object bodyMap = input.get("bodyMap");
        if (bodyMap instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return new HashMap<>();
    }

    @Override
    public String encryptFromMap(Map<String, Object> plain) {
        try {
            return OM.writeValueAsString(plain == null ? Map.of() : plain);
        } catch (Exception e) {
            throw new RuntimeException("默认出站加密失败", e);
        }
    }
}

