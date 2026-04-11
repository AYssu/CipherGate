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
    /** 设备唯一标识（同一应用下同一用户不重复） */
    private String deviceId;
    /** 设备名称（展示用） */
    private String deviceName;
    /** 设备系统，如 Windows / Android / iOS */
    private String deviceOs;

    private Long ts;
    private String nonce;
    private Long seq;
}

