package com.ayssu.ciphergate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 活动日志注解
 * 用于标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ActivityLog {
    
    /**
     * 操作类型
     * 如：LOGIN, LOGOUT, CREATE, UPDATE, DELETE, VIEW
     */
    String actionType();
    
    /**
     * 操作对象
     * 如：USER_MANAGEMENT, ROLE_MANAGEMENT, PERMISSION_MANAGEMENT
     */
    String actionTarget();
    
    /**
     * 操作描述
     */
    String description();
}
