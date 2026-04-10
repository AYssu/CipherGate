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

