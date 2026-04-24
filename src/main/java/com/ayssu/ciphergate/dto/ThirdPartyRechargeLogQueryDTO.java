package com.ayssu.ciphergate.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ThirdPartyRechargeLogQueryDTO {
    private Long appId;
    private Long credentialId;
    private String userEmail;
    private Integer status;
    private String requestIp;
    private String outTradeNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer current = 1;
    private Integer size = 10;
}
