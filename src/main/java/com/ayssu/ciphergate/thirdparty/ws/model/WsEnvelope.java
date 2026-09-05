package com.ayssu.ciphergate.thirdparty.ws.model;

import lombok.Data;

import java.util.Map;

/**
 * WebSocket message envelope (outer JSON).
 */
@Data
public class WsEnvelope {
    private String type;
    private String connId;
    private Long ts;
    private Long seq;
    /** HEARTBEAT：变量包序号（与 HKDF 子密钥派生一致，明文）。 */
    private Long varPacketSeq;
    private String nonce;

    // HELLO fields
    private String appKey;
    private String clientPubKey;

    // HELLO_ACK fields
    private String serverPubKey;
    private String serverNonce;

    // Encrypted payload wrapper
    private WsCipher cipher;

    // FUNC_CALL / FUNC_RESULT / FUNC_ERROR fields
    /** 插件ID（FUNC_CALL 可选，不传则自动查找） */
    private String pluginId;
    /** 函数名称 */
    private String func;
    /** 请求ID（用于匹配请求和响应） */
    private String reqId;
    /** 函数参数（FUNC_CALL） */
    private Map<String, Object> params;
    /** 函数返回数据（FUNC_RESULT） */
    private Map<String, Object> data;
    /** 错误码（FUNC_ERROR） */
    private String code;
    /** 错误信息（FUNC_ERROR） */
    private String message;
}

