package com.ciphergate.plugin.rsa;

/**
 * 加密插件通用异常基类。
 */
public class CryptoPluginException extends RuntimeException {

    public CryptoPluginException(String message) {
        super(message);
    }

    public CryptoPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
