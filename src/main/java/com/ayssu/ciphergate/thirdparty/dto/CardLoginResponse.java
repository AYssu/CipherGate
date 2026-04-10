package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CardLoginResponse {
    private Long appId;
    private Long cardId;
    private String cardCode;
    private LocalDateTime expiresAt;
    /**
     * 剩余可用秒数（expiresAt - now）
     */
    private Long available;
    /**
     * 使用次数（对齐 EasyVerify bind_number 语义）
     */
    private Integer bindNumber;
    /**
     * 应用变量（默认返回）
     */
    private Map<String, Object> variables;
    private Boolean online;
}

