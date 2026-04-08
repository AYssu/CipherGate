package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用变量历史记录实体类
 */
@Data
@TableName("app_variable_history")
public class AppVariableHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 变量ID
     */
    private Long variableId;
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 变量名称
     */
    private String variableName;
    
    /**
     * 操作类型: CREATE, UPDATE, DELETE
     */
    private String operationType;
    
    /**
     * 变更前的值 (JSON格式)
     */
    private String oldValue;
    
    /**
     * 变更后的值 (JSON格式)
     */
    private String newValue;
    
    /**
     * 变更原因/备注
     */
    private String changeReason;
    
    /**
     * 操作者ID
     */
    private Long operatorId;
    
    /**
     * 操作者IP
     */
    private String operatorIp;
    
    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 应用名称（非数据库字段）
     */
    @TableField(exist = false)
    private String appName;
    
    /**
     * 操作者用户名（非数据库字段）
     */
    @TableField(exist = false)
    private String operatorUsername;
}