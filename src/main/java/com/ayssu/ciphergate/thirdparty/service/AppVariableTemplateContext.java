package com.ayssu.ciphergate.thirdparty.service;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppVariableTemplateContext {
    private Long appId;
    private String appKey;

    private Long userId;
    private String username;
    private LocalDateTime memberExpiresAt;

    private String wsConnId;
    private Long wsConnectedAtEpochMs;
    private Long wsOnlineSeconds;

    private String clientIp;
    private String deviceId;
    private String deviceName;
    private String deviceOs;

    private Integer userLoginCount;
    private LocalDateTime userLastLoginAt;
    private String userLastLoginIp;
}
