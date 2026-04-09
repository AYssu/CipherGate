package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenLoginResponse {
    private Long appId;
    private Long userId;
    private Long bindId;
    private LocalDateTime expiresAt;
}

