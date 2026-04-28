package com.ayssu.ciphergate.thirdparty.crypto;

import com.ayssu.ciphergate.service.PluginModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoRuntimeService {
    private static final String DEFAULT_LOCAL_PLUGIN_ID = "aes-default";

    private final List<CryptoPlugin> localPlugins;
    private final PluginManager pluginManager;
    private final PluginModuleService pluginModuleService;

    /**
     * 将 {@code plugin_module.config_values} 注入到加解密入参 Map，键名为 {@code pluginConfig}（与 {@code encryptionConfig} 区分）。
     */
    private void mergePluginRuntimeConfig(Map<String, Object> map, String logicalPluginId) {
        if (map == null || !StringUtils.hasText(logicalPluginId)) {
            return;
        }
        Map<String, Object> fromDb = pluginModuleService.resolveRuntimeConfigValues(logicalPluginId);
        if (!fromDb.isEmpty()) {
            map.put("pluginConfig", fromDb);
            log.debug("已合并插件库表配置到入参: logicalPluginId={}, keys={}", logicalPluginId, fromDb.keySet());
        }
    }

    public Map<String, Object> decryptToMap(String pluginId, Map<String, Object> input) {
        Map<String, Object> work = new LinkedHashMap<>(input == null ? Map.of() : input);
        List<CryptoPlugin> extensions = pluginManager.getExtensions(CryptoPlugin.class);
        log.debug("CryptoPlugin自动模式: pluginExtensionsCount={}, localPluginsCount={}",
                extensions.size(), localPlugins.size());
        if (!extensions.isEmpty()) {
            for (CryptoPlugin extension : extensions) {
                log.debug("CryptoPlugin插件扩展: class={}, pluginId={}",
                        extension.getClass().getName(), extension.pluginId());
            }
        }

        // Interface-level replacement: if caller doesn't specify pluginId (or uses the default local id),
        // prefer any plugin extension without requiring business-layer changes.
        if (!StringUtils.hasText(pluginId) || DEFAULT_LOCAL_PLUGIN_ID.equals(pluginId)) {
            if (!extensions.isEmpty()) {
                CryptoPlugin picked = extensions.get(0);
                log.debug("CryptoPlugin自动模式命中插件实现: class={}, pluginId={}",
                        picked.getClass().getName(), picked.pluginId());
                mergePluginRuntimeConfig(work, picked.pluginId());
                return picked.decryptToMap(work);
            }
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        for (CryptoPlugin ext : extensions) {
            if (pluginId.equals(ext.pluginId())) {
                log.debug("CryptoPlugin命中插件实现: class={}, pluginId={}",
                        ext.getClass().getName(), ext.pluginId());
                mergePluginRuntimeConfig(work, ext.pluginId());
                return ext.decryptToMap(work);
            }
        }

        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId())) {
                log.debug("CryptoPlugin回退本地实现: class={}, pluginId={}",
                        local.getClass().getName(), local.pluginId());
                mergePluginRuntimeConfig(work, local.pluginId());
                return local.decryptToMap(work);
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
        Map<String, Object> safePlain = new HashMap<>(plain == null ? Map.of() : new LinkedHashMap<>(plain));
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
                log.debug("CryptoPlugin出站自动模式命中插件实现: class={}, pluginId={}",
                        picked.getClass().getName(), picked.pluginId());
                mergePluginRuntimeConfig(safePlain, picked.pluginId());
                return new CryptoEncryptedPayload(picked.pluginId(), picked.encryptFromMap(safePlain));
            }
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        for (CryptoPluginEncryptor ext : encryptorExtensions) {
            if (pluginId.equals(ext.pluginId())) {
                log.debug("CryptoPlugin出站命中插件实现: class={}, pluginId={}",
                        ext.getClass().getName(), ext.pluginId());
                mergePluginRuntimeConfig(safePlain, ext.pluginId());
                return new CryptoEncryptedPayload(ext.pluginId(), ext.encryptFromMap(safePlain));
            }
        }

        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId()) && local instanceof CryptoPluginEncryptor enc) {
                log.debug("CryptoPlugin出站回退本地实现: class={}, pluginId={}",
                        local.getClass().getName(), local.pluginId());
                mergePluginRuntimeConfig(safePlain, local.pluginId());
                return new CryptoEncryptedPayload(local.pluginId(), enc.encryptFromMap(safePlain));
            }
        }

        log.warn("CryptoPluginEncryptor not found, pluginId={}", pluginId);
        throw new RuntimeException("未找到出站加密插件: " + pluginId);
    }
}

