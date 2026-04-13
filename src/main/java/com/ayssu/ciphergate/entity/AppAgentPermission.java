package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("app_agent_permission")
public class AppAgentPermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String permissionCode;

    private LocalDateTime createdAt;
}

