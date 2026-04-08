package com.ayssu.ciphergate.dto;

import lombok.Data;

/**
 * 应用变量查询DTO
 */
@Data
public class AppVariableQueryDTO {
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 变量名称（模糊查询）
     */
    private String variableName;
    
    /**
     * 显示名称（模糊查询）
     */
    private String displayName;
    
    /**
     * 变量类型
     */
    private String variableType;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 标签（模糊查询）
     */
    private String tag;
    
    /**
     * 创建者ID
     */
    private Long createdBy;
    
    /**
     * 当前页码
     */
    private Integer current = 1;
    
    /**
     * 每页大小
     */
    private Integer size = 10;
}