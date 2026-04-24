package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("third_party_recharge_log")
public class ThirdPartyRechargeLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long credentialId;

    private Long appId;

    private String apiKey;

    private String userEmail;

    private Integer days;

    private String outTradeNo;

    private String requestIp;

    private Long requestTs;

    private Integer signValid;

    /** 1=成功,2=失败 */
    private Integer status;

    private String errorCode;

    private String errorMessage;

    private Integer idempotentHit;

    private LocalDateTime beforeExpiresAt;

    private LocalDateTime afterExpiresAt;

    private String traceId;

    private LocalDateTime createdAt;
}
