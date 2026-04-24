package com.ayssu.ciphergate.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ThirdPartyCredentialDTO {
    private Long appId;
    private String name;
    private String allowedIps;
    private Integer dailyLimit;
    private Long totalCallLimit;
    private Long totalDaysLimit;
    private LocalDateTime expiresAt;
    private Integer status;
    private String remark;
}
