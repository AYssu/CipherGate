package com.ayssu.ciphergate.thirdparty.crypto;

import com.ayssu.ciphergate.service.PluginModuleService;
import com.ciphergate.plugin.api.CryptoPluginEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

        // PF4J 插件发现：反射方式绕过 classloader 类型匹配问题
        List<DiscoveredPlugin> discovered = discoverCryptoPlugins();
        log.debug("CryptoPlugin发现: pf4jCount={}, localCount={}", discovered.size(), localPlugins.size());

        // 无指定 pluginId 或使用默认插件 → 回退本地 AES 插件
        // 不自动选 PF4J 插件，避免未配置 RSA 密钥的应用误用 RSA 插件
        if (!StringUtils.hasText(pluginId) || DEFAULT_LOCAL_PLUGIN_ID.equals(pluginId)) {
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        // 按 pluginId 查找 PF4J 插件
        for (DiscoveredPlugin dp : discovered) {
            if (pluginId.equals(dp.pluginId())) {
                mergePluginRuntimeConfig(work, dp.pluginId());
                return invokeDecrypt(dp, work);
            }
        }

        // 回退本地 Spring Bean
        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId())) {
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

        // PF4J 插件发现：反射方式绕过 classloader 类型匹配问题
        List<DiscoveredPlugin> discovered = discoverCryptoPlugins().stream()
                .filter(DiscoveredPlugin::hasEncrypt)
                .toList();

        // Auto mode → 回退本地 AES 插件，不自动选 PF4J 插件
        if (!StringUtils.hasText(pluginId) || DEFAULT_LOCAL_PLUGIN_ID.equals(pluginId)) {
            pluginId = DEFAULT_LOCAL_PLUGIN_ID;
        }

        // 按 pluginId 查找 PF4J 插件
        for (DiscoveredPlugin dp : discovered) {
            if (pluginId.equals(dp.pluginId())) {
                mergePluginRuntimeConfig(safePlain, dp.pluginId());
                return new CryptoEncryptedPayload(dp.pluginId(), invokeEncrypt(dp, safePlain));
            }
        }

        // 回退本地 Spring Bean（反射调用）
        for (CryptoPlugin local : localPlugins) {
            if (pluginId.equals(local.pluginId())) {
                try {
                    var method = local.getClass().getMethod("encryptFromMap", Map.class);
                    String result = (String) method.invoke(local, safePlain);
                    mergePluginRuntimeConfig(safePlain, local.pluginId());
                    return new CryptoEncryptedPayload(local.pluginId(), result);
                } catch (Exception e) {
                    log.warn("本地插件 encryptFromMap 调用失败: class={}", local.getClass().getName(), e);
                }
            }
        }

        log.warn("CryptoPluginEncryptor not found, pluginId={}", pluginId);
        throw new RuntimeException("未找到出站加密插件: " + pluginId);
    }

    // ========== 反射调用辅助 ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeDecrypt(DiscoveredPlugin dp, Map<String, Object> input) {
        try {
            var method = dp.instance().getClass().getMethod("decryptToMap", Map.class);
            return (Map<String, Object>) method.invoke(dp.instance(), input);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            log.error("插件解密异常: pluginId={}, causeType={}, causeMsg={}",
                    dp.pluginId(),
                    cause != null ? cause.getClass().getName() : "null",
                    cause != null ? cause.getMessage() : "null");
            throw new RuntimeException("插件 decryptToMap 调用失败: " + dp.pluginId(), e);
        } catch (Exception e) {
            throw new RuntimeException("插件 decryptToMap 调用失败: " + dp.pluginId(), e);
        }
    }

    private String invokeEncrypt(DiscoveredPlugin dp, Map<String, Object> plain) {
        try {
            var method = dp.instance().getClass().getMethod("encryptFromMap", Map.class);
            return (String) method.invoke(dp.instance(), plain);
        } catch (Exception e) {
            throw new RuntimeException("插件 encryptFromMap 调用失败: " + dp.pluginId(), e);
        }
    }

    /**
     * 从 PF4J 已加载插件中发现加密插件。
     * 绕过 getExtensions(Class) 的 classloader 类型匹配问题，
     * 直接遍历插件 classloader 扫描 @Extension 注解类。
     */
    private record DiscoveredPlugin(String pluginId, Object instance, boolean hasEncrypt) {}

    private List<DiscoveredPlugin> discoverCryptoPlugins() {
        List<DiscoveredPlugin> result = new ArrayList<>();

        // 遍历所有已加载插件
        for (org.pf4j.PluginWrapper pw : pluginManager.getPlugins()) {
            if (pw.getPlugin() == null) continue;
            ClassLoader cl = pw.getPluginClassLoader();
            log.debug("扫描插件: pluginId={}, classloader={}", pw.getPluginId(), cl.getClass().getName());

            // 方式1: 读 extensions.idx
            boolean found = scanExtensionsIndex(cl, result);

            // 方式2: 扫描 JAR 中所有 .class 文件
            if (!found) {
                scanPluginJar(pw, cl, result);
            }
        }

        log.debug("加密插件发现: count={}, plugins={}", result.size(),
                result.stream().map(d -> d.pluginId() + "(" + d.instance().getClass().getName() + ")").toList());
        return result;
    }

    private boolean scanExtensionsIndex(ClassLoader cl, List<DiscoveredPlugin> result) {
        try {
            java.io.InputStream is = cl.getResourceAsStream("META-INF/extensions.idx");
            if (is == null) return false;
            String index = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            log.debug("extensions.idx 内容: {}", index);
            for (String className : index.split("\n")) {
                className = className.trim();
                if (className.isEmpty()) continue;
                try {
                    Class<?> clazz = cl.loadClass(className);
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    DiscoveredPlugin dp = tryAdapt(instance);
                    if (dp != null) result.add(dp);
                } catch (Exception e) {
                    log.debug("加载扩展类失败: {}", className, e);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void scanPluginJar(org.pf4j.PluginWrapper pw, ClassLoader cl, List<DiscoveredPlugin> result) {
        // 通过 classloader 的 URL 扫描 JAR 中的类
        try {
            if (cl instanceof java.net.URLClassLoader urlCl) {
                for (java.net.URL url : urlCl.getURLs()) {
                    if (!url.toString().endsWith(".jar")) continue;
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new java.io.File(url.toURI()))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.endsWith(".class") || name.contains("$")) continue;
                            String className = name.replace('/', '.').replace(".class", "");
                            try {
                                Class<?> clazz = cl.loadClass(className);
                                if (clazz.isAnnotationPresent(org.pf4j.Extension.class)) {
                                    Object instance = clazz.getDeclaredConstructor().newInstance();
                                    DiscoveredPlugin dp = tryAdapt(instance);
                                    if (dp != null) result.add(dp);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("扫描 JAR 失败: pluginId={}", pw.getPluginId(), e);
        }
    }

    /**
     * 尝试将对象适配为 DiscoveredPlugin（通过反射检查 pluginId 方法）。
     */
    private DiscoveredPlugin tryAdapt(Object obj) {
        try {
            var pidMethod = obj.getClass().getMethod("pluginId");
            String pid = (String) pidMethod.invoke(obj);
            if (pid == null || pid.isBlank()) return null;
            boolean hasEncrypt = false;
            try {
                obj.getClass().getMethod("encryptFromMap", Map.class);
                hasEncrypt = true;
            } catch (NoSuchMethodException ignored) {
            }
            return new DiscoveredPlugin(pid, obj, hasEncrypt);
        } catch (Exception e) {
            return null;
        }
    }
}

