package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CardLoginResponse {
    private Long appId;
    private LocalDateTime expiresAt;
    private Boolean online;
}

