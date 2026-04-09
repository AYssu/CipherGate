package com.ayssu.ciphergate.thirdparty.crypto;

/**
 * Outbound encryption result.
 *
 * @param pluginId the actual pluginId used to encrypt
 * @param data     opaque encrypted payload (e.g. HEX)
 */
public record CryptoEncryptedPayload(String pluginId, String data) {
}

