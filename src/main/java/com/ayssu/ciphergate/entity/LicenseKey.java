package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 卡密实体类
 */
@Data
@TableName(value = "license_key", autoResultMap = true)
public class LicenseKey implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属应用ID
     */
    private Long appId;
    
    /**
     * 创建者ID
     */
    private Long ownerId;
    
    /**
     * 卡密码
     */
    private String keyCode;
    
    /**
     * 卡密类型
     */
    private String keyType;
    
    /**
     * 时长数值
     */
    private Integer durationValue;
    
    /**
     * 时长单位
     */
    private String durationUnit;
    
    /**
     * 批次ID
     */
    private Long batchId;
    
    /**
     * 来源
     */
    private String source;
    
    /**
     * 绑定设备标识
     */
    private String bindDeviceId;
    
    /**
     * 绑定IP
     */
    private String bindIp;
    
    /**
     * 绑定的终端用户ID
     */
    private Long bindUserId;
    
    /**
     * 首次使用时间
     */
    private LocalDateTime firstUsedAt;
    
    /**
     * 最后使用时间
     */
    private LocalDateTime lastUsedAt;
    
    /**
     * 到期时间
     */
    private LocalDateTime expiresAt;
    
    /**
     * 使用次数
     */
    private Integer useCount;
    
    /**
     * 使用次数限制
     */
    private Integer useLimit;
    
    /**
     * 解绑次数
     */
    private Integer unbindCount;
    
    /**
     * 解绑次数限制
     */
    private Integer unbindLimit;
    
    /**
     * 可使用时间段-开始
     */
    private LocalTime useTimeStart;
    
    /**
     * 可使用时间段-结束
     */
    private LocalTime useTimeEnd;
    
    /**
     * 是否验证设备
     */
    private Boolean deviceCheckEnabled;
    
    /**
     * 是否验证IP
     */
    private Boolean ipCheckEnabled;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatAt;
    
    /**
     * 心跳间隔(秒)
     */
    private Integer heartbeatInterval;
    
    /**
     * 当前连接ID
     */
    private String connectionId;
    
    /**
     * 是否在线
     */
    private Boolean isOnline;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 核心标记数据
     */
    private String coreData;
    
    /**
     * 扩展元数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
    
    /**
     * 状态: 1=未使用, 2=使用中, 3=已到期(已过期), 4=已禁用
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 应用名称（非数据库字段）
     */
    @TableField(exist = false)
    private String appName;
    
    /**
     * 创建者名称（非数据库字段）
     */
    @TableField(exist = false)
    private String ownerName;
    
    /**
     * 批次名称（非数据库字段）
     */
    @TableField(exist = false)
    private String batchName;
}
