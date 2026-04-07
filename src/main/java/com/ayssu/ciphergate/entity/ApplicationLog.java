package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用操作日志实体类
 */
@Data
@TableName(value = "application_log", autoResultMap = true)
public class ApplicationLog implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人名称
     */
    private String operatorName;
    
    /**
     * 操作类型
     */
    private String operation;
    
    /**
     * 操作描述
     */
    private String operationDesc;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * User Agent
     */
    private String userAgent;
    
    /**
     * 请求参数(JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestParams;
    
    /**
     * 响应结果: SUCCESS, FAILED
     */
    private String responseResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 操作时间
     */
    private LocalDateTime createdAt;
}
