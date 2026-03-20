package com.ayssu.ciphergate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayssu.ciphergate.entity.Permission;
import com.ayssu.ciphergate.mapper.PermissionMapper;
import com.ayssu.ciphergate.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    
    private final PermissionMapper permissionMapper;
    
    @Override
    public List<Permission> getAllPermissions() {
        QueryWrapper<Permission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        queryWrapper.orderByAsc("resource_type", "permission_code");
        return list(queryWrapper);
    }
    
    @Override
    public Permission getPermissionByCode(String permissionCode) {
        QueryWrapper<Permission> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("permission_code", permissionCode);
        return getOne(queryWrapper);
    }
    
    @Override
    @Transactional
    public boolean createPermission(Permission permission) {
        // 设置默认值
        if (permission.getStatus() == null) {
            permission.setStatus(1);
        }
        if (permission.getResourceType() == null) {
            permission.setResourceType("API");
        }
        
        return save(permission);
    }
    
    @Override
    @Transactional
    public boolean updatePermission(Permission permission) {
        return updateById(permission);
    }
    
    @Override
    @Transactional
    public boolean deletePermission(Long id) {
        return removeById(id);
    }
    
    @Override
    @Transactional
    public boolean batchDeletePermissions(List<Long> ids) {
        // 检查每个权限是否被使用
        for (Long id : ids) {
            if (isPermissionInUse(id)) {
                throw new RuntimeException("权限ID " + id + " 正在被角色使用，无法删除");
            }
        }
        return removeByIds(ids);
    }
    
    @Override
    public boolean isPermissionInUse(Long permissionId) {
        return permissionMapper.countRolePermissions(permissionId) > 0;
    }
    
    @Override
    public List<Permission> getPermissionsByRoleId(Long roleId) {
        return permissionMapper.selectPermissionsByRoleId(roleId);
    }
}