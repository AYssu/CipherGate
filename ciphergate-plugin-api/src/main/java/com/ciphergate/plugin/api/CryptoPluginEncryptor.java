package com.ciphergate.plugin.api;

import java.util.Map;

/**
 * 出站加密扩展接口。
 * 实现此接口表示插件支持双向加解密（入站解密 + 出站加密）。
 */
public interface CryptoPluginEncryptor extends CryptoPlugin {

    /**
     * 出站加密。
     *
     * @param plain 明文 key-value Map
     * @return 加密后的密文字符串（如 base64 / hex）
     */
    String encryptFromMap(Map<String, Object> plain);
}
