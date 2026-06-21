package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_membership")
public class UserMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long levelId;
    private Integer appUsed;
    private Long licenseUsed;
    private Long userRegisterUsed;
    private Long trafficUsed;
    private Long balance;
    private String inviteCode;
    private Long invitedBy;
    private Integer inviteCount;
    private LocalDateTime memberExpiresAt;
    private LocalDate lastCheckinDate;
    private Integer consecutiveCheckinDays;
    private Integer totalCheckinDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private MembershipLevel level;
}
