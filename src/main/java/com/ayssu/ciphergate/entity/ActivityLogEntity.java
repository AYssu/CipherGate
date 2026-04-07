package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动日志实体
 */
@Data
@TableName("activity_log")
public class ActivityLogEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String actionType;
    
    private String actionTarget;
    
    private String actionDescription;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String status;
    
    /**
     * 重要程度：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急
     */
    private String importanceLevel;
    
    /**
     * 是否已读
     */
    private Boolean isRead;
    
    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
    
    private LocalDateTime createdTime;
}
