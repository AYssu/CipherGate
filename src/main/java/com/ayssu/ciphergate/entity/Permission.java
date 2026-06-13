package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("permissions")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 权限名称
     */
    @TableField("permission_name")
    private String permissionName;
    
    /**
     * 权限编码
     */
    @TableField("permission_code")
    private String permissionCode;
    
    /**
     * 资源类型：API, MENU, BUTTON
     */
    @TableField("resource_type")
    private String resourceType;
    
    /**
     * 资源路径
     */
    @TableField("resource_path")
    private String resourcePath;
    
    /**
     * HTTP方法：GET, POST, PUT, DELETE
     */
    @TableField("http_method")
    private String httpMethod;
    
    /**
     * 权限描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 权限状态：1-启用，0-禁用
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}