package com.ayssu.ciphergate.thirdparty.crypto;

import org.pf4j.ExtensionPoint;

import java.util.Map;

public interface CryptoPlugin extends ExtensionPoint {
    String pluginId();

    /**
     * input: any data you need (headers, encrypted data, canonical source...).
     * output: decrypted payload map.
     */
    Map<String, Object> decryptToMap(Map<String, Object> input);
}

