package com.ciphergate.plugin.rsa;

/**
 * 加密失败异常。
 */
public class CryptoEncryptionException extends CryptoPluginException {

    public CryptoEncryptionException(String message) {
        super(message);
    }

    public CryptoEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
