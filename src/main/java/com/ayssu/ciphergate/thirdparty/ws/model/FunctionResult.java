package com.ayssu.ciphergate.thirdparty.ws.model;

import java.util.Map;

/**
 * 函数执行结果封装。
 *
 * @param success  是否成功
 * @param data     成功时的返回数据
 * @param code     失败时的错误码
 * @param message  失败时的错误信息
 */
public record FunctionResult(boolean success, Map<String, Object> data, String code, String message) {

    public static FunctionResult ok(Map<String, Object> data) {
        return new FunctionResult(true, data, null, null);
    }

    public static FunctionResult error(String code, String message) {
        return new FunctionResult(false, null, code, message);
    }
}
