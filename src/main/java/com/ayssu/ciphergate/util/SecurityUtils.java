package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 安全工具类
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {
    
    private final RoleMapper roleMapper;
    
    /**
     * 判断用户是否是管理员
     * @param userId 用户ID
     * @return 是否是管理员
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        
        // 查询用户的所有角色
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        
        // 检查是否有管理员角色
        return roles.stream()
                .anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()) 
                               || "ADMIN".equals(role.getRoleCode()));
    }
    
    /**
     * 判断用户是否有指定角色
     * @param userId 用户ID
     * @param roleCode 角色编码
     * @return 是否有该角色
     */
    public boolean hasRole(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        
        return roles.stream()
                .anyMatch(role -> roleCode.equals(role.getRoleCode()));
    }
    
    /**
     * 判断用户是否有任意一个指定角色
     * @param userId 用户ID
     * @param roleCodes 角色编码列表
     * @return 是否有任意一个角色
     */
    public boolean hasAnyRole(Long userId, String... roleCodes) {
        if (userId == null || roleCodes == null) {
            return false;
        }
        
        for (String roleCode : roleCodes) {
            if (hasRole(userId, roleCode)) {
                return true;
            }
        }
        
        return false;
    }
}
