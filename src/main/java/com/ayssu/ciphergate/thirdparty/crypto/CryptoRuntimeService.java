package com.ayssu.ciphergate.thirdparty.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoRuntimeService {
    private static final String DEFAULT_LOCAL_PLUGIN_ID = "rsa-default";

    private final List<CryptoPlugin> localPlugins;
    private final PluginManager pluginManager;

    public Map<String, Object> decryptToMap(String pluginId, Map<String, Object> input) {
        List<CryptoPlugin> extensions = pluginManager.getExtensions(CryptoPlugin.class);
        log.info("CryptoPlugin自动模式: pluginExtensionsCount={}, localPluginsCount={}",
                extensions.size(), localPlugins.size());
        if (!extensions.isEmpty()) {
            for (CryptoPlugin extension : extensions) {
                log.info("CryptoPlugin插件扩展: class={}, pluginId={}",
                        extension.getClass().getName(), extension.pluginId());
            }
        }

        // Interface-level replacement: if caller doesn't specify pluginId (or uses the default local id),
        // prefer any plugin extension without requiring business-layer changes.
        if (!StringUtils.hasText(pluginId) || DEFAULT_LOCAL_PLUGIN_ID.equals(pluginId)) {
            if (!extensions.isEmpty()) {
                CryptoPlugin picked = extensions.get(0);
                log.info("CryptoPlugin自动模式命中插件实现: class={}, pluginId={}",
                        picked.getClass().getName(), picked.pluginId());
                return picked.decryptToMap(input);
            }
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        for (CryptoPlugin ext : extensions) {
            if (pluginId.equals(ext.pluginId())) {
                log.info("CryptoPlugin命中插件实现: class={}, pluginId={}",
                        ext.getClass().getName(), ext.pluginId());
                return ext.decryptToMap(input);
            }
        }

        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId())) {
                log.info("CryptoPlugin回退本地实现: class={}, pluginId={}",
                        local.getClass().getName(), local.pluginId());
                return local.decryptToMap(input);
            }
        }

        log.warn("CryptoPlugin not found, pluginId={}", pluginId);
        throw new RuntimeException("未找到加解密插件: " + pluginId);
    }

    /**
     * Encrypt outbound payload using plugin (preferred) or local fallback.
     * Returned string is an opaque "data" field for third parties (e.g. HEX).
     */
    public String encryptFromMap(String pluginId, Map<String, Object> plain) {
        return encryptPayloadFromMap(pluginId, plain).data();
    }

    public CryptoEncryptedPayload encryptPayloadFromMap(String pluginId, Map<String, Object> plain) {
        return encryptPayloadFromMap(pluginId, plain, null);
    }

    public CryptoEncryptedPayload encryptPayloadFromMap(String pluginId, Map<String, Object> plain, Map<String, Object> encryptionConfig) {
        Map<String, Object> safePlain = new java.util.HashMap<>(plain == null ? Map.of() : new LinkedHashMap<>(plain));
        if (encryptionConfig != null && !encryptionConfig.isEmpty()) {
            safePlain.put("encryptionConfig", encryptionConfig);
        }

        List<CryptoPlugin> extensions = pluginManager.getExtensions(CryptoPlugin.class);
        List<CryptoPluginEncryptor> encryptorExtensions = extensions.stream()
                .filter(p -> p instanceof CryptoPluginEncryptor)
                .map(p -> (CryptoPluginEncryptor) p)
                .toList();

        // Auto mode: if not specified (or default local id), prefer any plugin encryptor.
        if (!StringUtils.hasText(pluginId) || DEFAULT_LOCAL_PLUGIN_ID.equals(pluginId)) {
            if (!encryptorExtensions.isEmpty()) {
                CryptoPluginEncryptor picked = encryptorExtensions.get(0);
                log.info("CryptoPlugin出站自动模式命中插件实现: class={}, pluginId={}",
                        picked.getClass().getName(), picked.pluginId());
                return new CryptoEncryptedPayload(picked.pluginId(), picked.encryptFromMap(safePlain));
            }
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        for (CryptoPluginEncryptor ext : encryptorExtensions) {
            if (pluginId.equals(ext.pluginId())) {
                log.info("CryptoPlugin出站命中插件实现: class={}, pluginId={}",
                        ext.getClass().getName(), ext.pluginId());
                return new CryptoEncryptedPayload(ext.pluginId(), ext.encryptFromMap(safePlain));
            }
        }

        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId()) && local instanceof CryptoPluginEncryptor enc) {
                log.info("CryptoPlugin出站回退本地实现: class={}, pluginId={}",
                        local.getClass().getName(), local.pluginId());
                return new CryptoEncryptedPayload(local.pluginId(), enc.encryptFromMap(safePlain));
            }
        }

        log.warn("CryptoPluginEncryptor not found, pluginId={}", pluginId);
        throw new RuntimeException("未找到出站加密插件: " + pluginId);
    }
}

