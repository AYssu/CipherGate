package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建消息请求 DTO
 */
@Data
@Schema(description = "创建消息请求")
public class CreateMessageRequest {
    
    @Schema(description = "消息类型", example = "SYSTEM_NOTICE", required = true)
    private String messageType;
    
    @Schema(description = "消息标题", example = "系统维护通知", required = true)
    private String title;
    
    @Schema(description = "消息内容", example = "系统将于今晚22:00进行维护，预计持续2小时", required = true)
    private String content;
    
    @Schema(description = "重要程度：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急", 
            example = "HIGH", 
            allowableValues = {"LOW", "MEDIUM", "HIGH", "URGENT"},
            defaultValue = "LOW")
    private String importanceLevel = "LOW";
    
    @Schema(description = "目标类型：ALL-所有用户，USER-指定用户，ROLE-指定角色", 
            example = "ALL",
            allowableValues = {"ALL", "USER", "ROLE"},
            defaultValue = "ALL")
    private String targetType = "ALL";
    
    @Schema(description = "目标ID（当targetType为USER或ROLE时必填）", example = "1")
    private Long targetId;
}
