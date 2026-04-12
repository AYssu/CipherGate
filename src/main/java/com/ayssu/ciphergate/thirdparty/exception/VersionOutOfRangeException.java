package com.ayssu.ciphergate.thirdparty.exception;

/**
 * 三方客户端 {@code x.x.x} 版本不在应用配置的 [minVersion, currentVersion] 闭区间内。
 */
public class VersionOutOfRangeException extends RuntimeException {

    public VersionOutOfRangeException(String message) {
        super(message);
    }
}
