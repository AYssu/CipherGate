package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private LocalDate checkinDate;
    private Integer licenseReward;
    private Integer userRegisterReward;
    private Long trafficReward;
    private Integer consecutiveDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
