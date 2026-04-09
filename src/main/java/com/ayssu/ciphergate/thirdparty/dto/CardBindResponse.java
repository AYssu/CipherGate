package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CardBindResponse {
    private Long userId;
    private Long bindId;
    private LocalDateTime expiresAt;
}

