package com.ayssu.ciphergate.thirdparty.ws.model;

import lombok.Data;

/**
 * Decrypted AUTH payload (inside cipher.data).
 */
@Data
public class WsAuthPayload {
    private String appKey;
    private String appSig;

    private String username;
    private String password;
    private String deviceId;

    private Long ts;
    private String nonce;
    private Long seq;
}

