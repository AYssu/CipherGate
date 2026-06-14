package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HeartbeatResponse {
    private Long appId;
    private Long cardId;
    private String cardCode;
    /** 下次心跳使用的交换 token */
    private String newToken;
    private LocalDateTime expiresAt;
    /** 剩余可用秒数 */
    private Long available;
    /** 应用变量（JSON） */
    private String variables;
    private Boolean online;
    /** true 表示心跳间隔超过 60 秒，疑似多用户共用 */
    private Boolean potentiallyShared;
}
