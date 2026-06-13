package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("users")
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String githubId;
    private String login;
    private String name;
    private String email;
    private String avatarUrl;
    private String accessToken;

    /**
     * 密码（BCrypt加密存储，本地账号登录使用）
     */
    private String password;
    
    /**
     * 用户状态：1-正常，0-禁用
     */
    private Integer status;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    
    /**
     * 用户拥有的角色列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Role> roles;
    
    /**
     * 用户拥有的权限列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Permission> permissions;
    
    /**
     * 用户可访问的菜单列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Menu> menus;

    /**
     * 覆盖默认 toString，避免 Spring Session JDBC 存储时
     * PRINCIPAL_NAME 列超长（User 包含大量字段）
     */
    @Override
    public String toString() {
        return login != null ? login : "user:" + id;
    }
}