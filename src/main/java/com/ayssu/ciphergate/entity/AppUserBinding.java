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
 * 应用用户绑定实体类
 */
@Data
@TableName("app_user_binding")
public class AppUserBinding implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 应用ID
     */
    private Long appId;
    
    /**
     * 终端用户ID
     */
    private Long userId;
    
    /**
     * 绑定类型: LICENSE=卡密绑定, TRIAL=试用, VIP=会员
     */
    private String bindType;
    
    /**
     * 关联的卡密ID
     */
    private Long licenseKeyId;
    
    /**
     * 设备标识
     */
    private String deviceId;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 设备系统
     */
    private String deviceOs;
    
    /**
     * 设备IP
     */
    private String deviceIp;
    
    /**
     * 到期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    /**
     * 首次绑定时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstBindAt;
    
    /**
     * 最后活跃时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveAt;
    
    /**
     * 使用次数
     */
    private Integer useCount;
    
    /**
     * 解绑次数
     */
    private Integer unbindCount;
    
    /**
     * 是否试用
     */
    private Boolean isTrial;
    
    /**
     * 试用到期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime trialExpiresAt;
    
    /**
     * 允许解绑
     */
    private Boolean allowUnbind;
    
    /**
     * 是否封禁
     */
    private Boolean isBanned;
    
    /**
     * 封禁原因
     */
    private String banReason;
    
    /**
     * 封禁时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banAt;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 状态: 1=正常, 2=已过期, 3=已封禁, 4=已解绑
     */
    private Integer status;
    
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
     * 用户名（非数据库字段）
     */
    @TableField(exist = false)
    private String username;
    
    /**
     * 卡密码（非数据库字段）
     */
    @TableField(exist = false)
    private String licenseKeyCode;
}
