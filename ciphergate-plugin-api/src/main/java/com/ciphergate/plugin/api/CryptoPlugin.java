package com.ciphergate.plugin.api;

import org.pf4j.ExtensionPoint;

import java.util.Map;

/**
 * 加密插件接口（入站解密）。
 * <p>
 * 实现此接口的类用 {@code @Extension} 注解标记，由 PF4J 自动发现。
 */
public interface CryptoPlugin extends ExtensionPoint {

    /**
     * 插件唯一标识，与 plugin.properties 中 plugin.id 一致。
     */
    String pluginId();

    /**
     * 入站解密。
     *
     * @param input 包含 data（密文）、encryptionConfig（应用配置）、pluginConfig（插件配置）等
     * @return 解密后的 key-value Map
     */
    Map<String, Object> decryptToMap(Map<String, Object> input);
}
