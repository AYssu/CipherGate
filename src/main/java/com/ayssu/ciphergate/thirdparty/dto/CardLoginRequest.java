package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

@Data
public class CardLoginRequest {
    private String cardCode;

    private String deviceId;
}

