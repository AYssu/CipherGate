package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ayssu.ciphergate.entity.Role;

import java.util.List;

/**
 * 角色服务接口
 */
public interface RoleService extends IService<Role> {
    
    /**
     * 根据用户ID查询角色列表
     */
    List<Role> getRolesByUserId(Long userId);
    
    /**
     * 根据角色编码查询角色
     */
    Role getRoleByCode(String roleCode);
    
    /**
     * 为用户分配角色
     */
    boolean assignRolesToUser(Long userId, List<Long> roleIds);
    
    /**
     * 移除用户的所有角色
     */
    boolean removeUserRoles(Long userId);
    
    /**
     * 获取角色的权限列表
     */
    List<Role> getRolesWithPermissions();
    
    /**
     * 清除用户的所有角色
     */
    boolean clearUserRoles(Long userId);
}