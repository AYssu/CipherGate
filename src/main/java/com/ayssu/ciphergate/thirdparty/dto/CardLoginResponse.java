package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CardLoginResponse {
    private Long appId;
    private Long cardId;
    private String cardCode;
    /**
     * 卡密类型（映射 license_key.key_type）
     */
    private String cardType;
    private LocalDateTime expiresAt;
    /**
     * 剩余可用秒数（expiresAt - now）
     */
    private Long available;
    /**
     * 使用次数（对齐 EasyVerify bind_number 语义）
     */
    private Integer bindNumber;
    /**
     * 解绑次数
     */
    private Integer unbindCount;
    /**
     * 解绑次数上限
     */
    private Integer unbindLimit;
    /**
     * 使用次数上限
     */
    private Integer useLimit;
    /**
     * 卡密状态: 1=未使用,2=使用中,3=已过期,4=已禁用
     */
    private Integer status;
    /**
     * 首次使用时间
     */
    private LocalDateTime firstUsedAt;
    /**
     * 最近使用时间
     */
    private LocalDateTime lastUsedAt;
    /**
     * 核心数据（映射 license_key.core_data）
     */
    private String coreData;
    /**
     * 应用变量（默认返回）
     */
    private String variables;
    private Boolean online;
    /**
     * 心跳交换令牌（用于后续心跳接口，免费模式为 null）
     */
    private String token;
}

