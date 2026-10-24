package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("third_party_credential")
public class ThirdPartyCredential implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 兼容旧接口的首个绑定应用，授权判断以关联集合为准。 */
    private Long appId;

    @TableField(exist = false)
    private List<Long> appIds;

    private String name;

    private String apiKey;

    private String apiSecret;

    /** 1=启用, 0=禁用 */
    private Integer status;

    /** 逗号分隔IP */
    private String allowedIps;

    private Integer dailyLimit;

    private Long totalCallLimit;

    private Long totalDaysLimit;

    private Long usedCallCount;

    private Long usedDaysCount;

    private LocalDateTime expiresAt;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
