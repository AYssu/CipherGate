package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("app_agent")
public class AppAgent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private String agentCode;

    /** 绑定后台用户ID（users.id） */
    private Long userId;

    /** ALL_IN_APP / OWN_ONLY */
    private String scopeMode;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

