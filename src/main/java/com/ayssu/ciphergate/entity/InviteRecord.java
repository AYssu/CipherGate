package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("invite_record")
public class InviteRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inviterId;
    private Long inviteeId;
    private Long rewardAmount;
    private Boolean rewardGranted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
