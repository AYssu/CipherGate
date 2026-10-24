package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("app_user_license_recharge")
public class AppUserLicenseRecharge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long licenseKeyId;
    private Long appId;
    private Long appUserId;
    private String keyCodeMasked;
    private Integer durationValue;
    private String durationUnit;
    private LocalDateTime beforeExpiresAt;
    private LocalDateTime afterExpiresAt;
    private String requestIp;
    private LocalDateTime createdAt;
}
