package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 卡密换绑成功响应（出站前由统一 Advice 加密为 data HEX）。
 */
@Data
public class CardRebindResponse {

    private Long appId;
    private Long cardId;
    private String cardCode;
    private String deviceId;
    private LocalDateTime expiresAt;
    private Long available;
    private Map<String, Object> variables;
}
