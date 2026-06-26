package com.ayssu.ciphergate.portal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("portal_login_log")
public class PortalLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appUserId;
    private Long appId;
    private String loginIp;
    private String ipRegion;
    private String userAgent;
    private String deviceInfo;
    private String loginType;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
