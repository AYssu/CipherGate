package com.ayssu.ciphergate.thirdparty.crypto;

import java.util.Map;

public interface CryptoPluginEncryptor extends CryptoPlugin {
    String encryptFromMap(Map<String, Object> plain);
}
