package com.ayssu.ciphergate.portal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("portal_payment_order")
public class PortalPaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long appUserId;
    private Long appId;
    private Long planId;
    private String planName;
    private Integer durationDays;
    private Long amountFen;
    private String paymentChannel;
    private String tradeNo;
    private String payUrl;
    private Integer status;
    private LocalDateTime paidAt;
    private Boolean notifyReceived;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
