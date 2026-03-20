package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ayssu.ciphergate.entity.Permission;

import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService extends IService<Permission> {
    
    /**
     * 获取所有权限列表
     */
    List<Permission> getAllPermissions();
    
    /**
     * 根据权限编码获取权限
     */
    Permission getPermissionByCode(String permissionCode);
    
    /**
     * 创建权限
     */
    boolean createPermission(Permission permission);
    
    /**
     * 更新权限
     */
    boolean updatePermission(Permission permission);
    
    /**
     * 删除权限
     */
    boolean deletePermission(Long id);
    
    /**
     * 批量删除权限
     */
    boolean batchDeletePermissions(List<Long> ids);
    
    /**
     * 检查权限是否被角色使用
     */
    boolean isPermissionInUse(Long permissionId);
    
    /**
     * 根据角色ID获取权限列表
     */
    List<Permission> getPermissionsByRoleId(Long roleId);
}