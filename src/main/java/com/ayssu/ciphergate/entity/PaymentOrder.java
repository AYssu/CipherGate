package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("payment_order")
public class PaymentOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long productId;
    private String productType;
    private String productName;
    private Integer quantity;
    private Long totalAmount;
    private Long payAmount;
    private String paymentChannel;
    private String tradeNo;
    private String payUrl;
    private Integer status;
    private LocalDateTime paidAt;
    private Boolean adminGranted;
    private Long adminOperatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
