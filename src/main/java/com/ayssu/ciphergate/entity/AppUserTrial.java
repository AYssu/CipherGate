package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户试用记录
 */
@Data
@TableName("app_user_trial")
public class AppUserTrial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long userId;

    private LocalDateTime trialStartedAt;

    private LocalDateTime trialExpiresAt;

    private String deviceId;

    private LocalDateTime createdAt;
}
