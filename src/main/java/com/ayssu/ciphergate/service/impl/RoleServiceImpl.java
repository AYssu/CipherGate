package com.ayssu.ciphergate.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.entity.UserRole;
import com.ayssu.ciphergate.mapper.RoleMapper;
import com.ayssu.ciphergate.mapper.UserRoleMapper;
import com.ayssu.ciphergate.mapper.PermissionMapper;
import com.ayssu.ciphergate.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
    
    @Autowired
    private UserRoleMapper userRoleMapper;
    
    @Autowired
    private PermissionMapper permissionMapper;
    
    @Override
    public List<Role> getRolesByUserId(Long userId) {
        return baseMapper.selectRolesByUserId(userId);
    }
    
    @Override
    public Role getRoleByCode(String roleCode) {
        return baseMapper.selectByRoleCode(roleCode);
    }
    
    @Override
    @Transactional
    public boolean assignRolesToUser(Long userId, List<Long> roleIds) {
        // 先删除用户现有的角色
        userRoleMapper.deleteByUserId(userId);
        
        // 分配新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        return userRole;
                    })
                    .collect(Collectors.toList());
            
            for (UserRole userRole : userRoles) {
                userRoleMapper.insert(userRole);
            }
        }
        
        return true;
    }
    
    @Override
    public boolean removeUserRoles(Long userId) {
        return userRoleMapper.deleteByUserId(userId) >= 0;
    }
    
    @Override
    public List<Role> getRolesWithPermissions() {
        List<Role> roles = this.list();
        for (Role role : roles) {
            role.setPermissions(permissionMapper.selectPermissionsByRoleId(role.getId()));
        }
        return roles;
    }
    
    @Override
    @Transactional
    public boolean clearUserRoles(Long userId) {
        return userRoleMapper.deleteByUserId(userId) >= 0;
    }
}