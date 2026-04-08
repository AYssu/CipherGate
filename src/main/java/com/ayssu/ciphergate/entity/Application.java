package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用实体类
 */
@Data
@TableName(value = "application", autoResultMap = true)
public class Application implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属用户ID
     */
    private Long ownerId;
    
    /**
     * 应用名称
     */
    private String appName;
    
    /**
     * API密钥
     */
    private String appKey;
    
    /**
     * API密钥(加密存储)
     */
    private String appSecret;
    
    /**
     * 应用描述
     */
    private String description;
    
    /**
     * 应用公告
     */
    private String notice;
    
    /**
     * 更新公告
     */
    private String updateNotice;
    
    /**
     * 更新文件存储Key(MinIO)
     */
    private String updateFileStorageKey;
    
    /**
     * 应用分类
     */
    private String category;
    
    /**
     * 标签(逗号分隔)
     */
    private String tags;
    
    /**
     * 应用图标
     */
    private String iconUrl;
    
    /**
     * 业务模式: 1=付费, 2=免费, 3=试用+付费
     */
    private Integer businessModel;
    
    /**
     * 状态: 1=正常, 2=维护, 3=停用
     */
    private Integer status;
    
    /**
     * 加密插件标识
     */
    private String encryptionPlugin;
    
    /**
     * 加密配置参数(JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> encryptionConfig;
    
    /**
     * 功能开关配置(JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> features;
    
    /**
     * 流量限制(字节)
     */
    private Long trafficLimit;
    
    /**
     * 已使用流量
     */
    private Long trafficUsed;
    
    /**
     * 当前版本号
     */
    private String currentVersion;
    
    /**
     * 最低支持版本
     */
    private String minVersion;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 逻辑删除: 0=未删除, 1=已删除
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 所属用户名称（非数据库字段）
     */
    @TableField(exist = false)
    private String ownerName;
}
