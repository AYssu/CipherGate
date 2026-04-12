package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用终端用户实体类
 */
@Data
@TableName("app_user")
public class AppUser implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属应用ID
     */
    private Long appId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 密码(加密存储)
     */
    private String password;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 个性签名
     */
    private String signature;
    
    /**
     * 登录次数
     */
    private Integer loginCount;
    
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;
    
    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 最后登录设备标识（WS AUTH 上报的 deviceId）
     */
    private String lastDeviceId;

    /**
     * 会员到期时间；null 表示未开通会员。充值或管理员加时长时更新。
     */
    private LocalDateTime memberExpiresAt;
    
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
     * 应用名称（非数据库字段）
     */
    @TableField(exist = false)
    private String appName;
    
    /**
     * 绑定设备数量（非数据库字段）
     */
    @TableField(exist = false)
    private Integer bindingCount;

    /** 当前是否有 WS 会话在线（非数据库字段） */
    @TableField(exist = false)
    private Boolean wsOnline;

    /** 当前 WS 连接数（非数据库字段） */
    @TableField(exist = false)
    private Integer wsSessionCount;

    /** 最早一条当前 WS 会话连接时间（epoch 毫秒，非数据库字段） */
    @TableField(exist = false)
    private Long wsEarliestConnectedAtEpochMs;

    /** 相对最早会话的在线秒数（非数据库字段，列表/详情接口计算） */
    @TableField(exist = false)
    private Long wsOnlineSeconds;

    /** 当前是否在会员有效期内（非数据库字段） */
    @TableField(exist = false)
    private Boolean memberActive;

    /**
     * 是否存在至少一条未删除且已封禁的绑定（非数据库字段，列表/详情由服务填充）
     */
    @TableField(exist = false)
    private Boolean isBanned;
}
