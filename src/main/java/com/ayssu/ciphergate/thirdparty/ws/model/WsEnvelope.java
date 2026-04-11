package com.ayssu.ciphergate.thirdparty.ws.model;

import lombok.Data;

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
}

