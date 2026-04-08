package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卡密批次实体类
 */
@Data
@TableName("license_batch")
public class LicenseBatch implements Serializable {
    
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
    private Long creatorId;
    
    /**
     * 批次名称
     */
    private String batchName;
    
    /**
     * 批次编号
     */
    private String batchCode;
    
    /**
     * 卡密类型
     */
    private String keyType;
    
    /**
     * 时长数值
     */
    private Integer durationValue;
    
    /**
     * 时长单位: HOUR,DAY,MONTH,YEAR
     */
    private String durationUnit;
    
    /**
     * 生成总数
     */
    private Integer totalCount;
    
    /**
     * 已使用数量
     */
    private Integer usedCount;
    
    /**
     * 使用次数限制
     */
    private Integer useLimit;
    
    /**
     * 解绑次数限制
     */
    private Integer unbindLimit;
    
    /**
     * 是否验证设备
     */
    private Boolean deviceCheckEnabled;
    
    /**
     * 是否验证IP
     */
    private Boolean ipCheckEnabled;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 应用名称（非数据库字段）
     */
    @TableField(exist = false)
    private String appName;
    
    /**
     * 创建者名称（非数据库字段）
     */
    @TableField(exist = false)
    private String creatorName;
}
