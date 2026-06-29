package com.ciphergate.plugin.rsa;

/**
 * 解密失败异常。
 */
public class CryptoDecryptionException extends CryptoPluginException {

    public CryptoDecryptionException(String message) {
        super(message);
    }

    public CryptoDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
