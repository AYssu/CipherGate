package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用变量实体类
 */
@Data
@TableName("app_variable")
public class AppVariable implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 变量名称
     */
    private String variableName;
    
    /**
     * 变量显示名称
     */
    private String displayName;
    
    /**
     * 变量描述
     */
    private String description;
    
    /**
     * 变量类型: STRING, NUMBER, BOOLEAN, JSON, ARRAY
     */
    private String variableType;
    
    /**
     * 变量值 (JSON格式存储)
     */
    private String variableValue;
    
    /**
     * 是否必填
     */
    private Boolean required;
    
    /**
     * 排序权重
     */
    private Integer sortOrder;
    
    /**
     * 验证规则 (JSON格式)
     * 例如: {"minLength": 5, "maxLength": 100, "pattern": "^[a-zA-Z0-9]+$"}
     */
    private String validationRules;
    
    /**
     * 变量选项 (用于下拉框等)
     * JSON格式: [{"label": "选项1", "value": "value1"}, ...]
     */
    private String options;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 标签 (JSON数组格式)
     */
    private String tags;
    
    /**
     * 扩展元数据 (JSON格式)
     */
    private Map<String, Object> metadata;
    
    /**
     * 创建者ID
     */
    private Long createdBy;
    
    /**
     * 更新者ID
     */
    private Long updatedBy;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * 逻辑删除
     */
    private Integer deleted;

    /**
     * 安全分级：0=STANDARD，1=SENSITIVE，2=CRITICAL（WS 分桶；库表与 DTO 默认 2=CRITICAL；null 按 CRITICAL 处理）
     */
    private Integer securityTier;
    
    /**
     * 应用名称（非数据库字段）
     */
    @TableField(exist = false)
    private String appName;
    
    /**
     * 创建者用户名（非数据库字段）
     */
    @TableField(exist = false)
    private String createdByUsername;
    
    /**
     * 更新者用户名（非数据库字段）
     */
    @TableField(exist = false)
    private String updatedByUsername;
}