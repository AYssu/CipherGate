package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("app_agent_quota")
public class AppAgentQuota implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String keyType;

    private Long quotaTotal;

    private Long quotaUsed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

