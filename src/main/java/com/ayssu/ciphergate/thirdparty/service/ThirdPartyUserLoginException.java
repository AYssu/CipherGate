package com.ayssu.ciphergate.thirdparty.service;

/** 只包含可向客户端公开的用户登录错误。 */
public class ThirdPartyUserLoginException extends RuntimeException {
    private final int code;

    public ThirdPartyUserLoginException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
