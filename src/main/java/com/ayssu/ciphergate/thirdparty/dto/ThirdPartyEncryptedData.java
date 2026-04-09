package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * Outbound encrypted payload wrapper for third-party APIs.
 */
@Data
public class ThirdPartyEncryptedData {
    /**
     * Encrypted opaque payload (e.g. AES HEX).
     */
    private String data;

    /**
     * Optional hint for which plugin/algo to use on the client side.
     */
    private String pluginId;
}

