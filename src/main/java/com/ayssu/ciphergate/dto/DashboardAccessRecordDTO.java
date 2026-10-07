package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "近7天成功登录记录，仅应用 owner 可见，不包含卡密明文或心跳 token")
public class DashboardAccessRecordDTO {
    private Long id;
    private Long appId;
    private String appName;
    private String identityType;
    private String identityId;
    private String deviceHash;
    private String clientIp;
    private LocalDateTime createdAt;
}
