package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

@Data
public class CardBindRequest {
    private String appUserId;
    private String cardCode;

    private String deviceId;
    private String deviceName;
    private String deviceOs;
    private String ip;
    private String channel;
}

