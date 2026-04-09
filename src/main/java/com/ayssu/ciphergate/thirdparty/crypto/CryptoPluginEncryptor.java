package com.ayssu.ciphergate.thirdparty.crypto;

import java.util.Map;

/**
 * Optional extension for outbound encryption.
 * Implement this in PF4J plugins when you want the host to encrypt outbound responses.
 */
public interface CryptoPluginEncryptor extends CryptoPlugin {
    /**
     * Encrypt a plaintext payload map and return an opaque string (e.g. HEX).
     */
    String encryptFromMap(Map<String, Object> plain);
}

