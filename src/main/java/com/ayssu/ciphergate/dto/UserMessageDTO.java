package com.ayssu.ciphergate.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户消息DTO（包含已读状态）
 */
@Data
public class UserMessageDTO {
    
    private Long id;
    
    /**
     * 消息类型
     */
    private String messageType;
    
    /**
     * 消息标题
     */
    private String title;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 重要程度：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急
     */
    private String importanceLevel;
    
    /**
     * 目标类型：ALL-所有用户，USER-指定用户，ROLE-指定角色
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    private Long targetId;
    
    /**
     * 是否已发送邮件
     */
    private Boolean emailSent;
    
    /**
     * 邮件发送时间
     */
    private LocalDateTime emailSentTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 是否已读
     */
    private Boolean isRead;
}
