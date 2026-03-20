package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {
    
    @Autowired
    private RoleService roleService;
    
    /**
     * 获取角色列表
     */
    @GetMapping
    @RequirePermission(value = "ROLE_LIST", description = "查看角色列表")
    public Result<List<Role>> getRoles() {
        List<Role> roles = roleService.getRolesWithPermissions();
        return Result.success(roles);
    }
    
    /**
     * 创建角色
     */
    @PostMapping
    @RequirePermission(value = "ROLE_CREATE", description = "创建角色")
    public Result<Role> createRole(@RequestBody Role role) {
        boolean success = roleService.save(role);
        if (success) {
            return Result.success(role);
        } else {
            return Result.error("创建角色失败");
        }
    }
    
    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    @RequirePermission(value = "ROLE_UPDATE", description = "更新角色")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        boolean success = roleService.updateById(role);
        if (success) {
            return Result.success(role);
        } else {
            return Result.error("更新角色失败");
        }
    }
    
    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @RequirePermission(value = "ROLE_DELETE", description = "删除角色")
    public Result<String> deleteRole(@PathVariable Long id) {
        boolean success = roleService.removeById(id);
        if (success) {
            return Result.success("删除成功");
        } else {
            return Result.error("删除角色失败");
        }
    }
    
    /**
     * 为用户分配角色
     */
    @PostMapping("/assign")
    @RequirePermission(value = "ROLE_UPDATE", description = "分配角色")
    public Result<String> assignRoles(@RequestParam Long userId, @RequestParam List<Long> roleIds) {
        boolean success = roleService.assignRolesToUser(userId, roleIds);
        if (success) {
            return Result.success("角色分配成功");
        } else {
            return Result.error("角色分配失败");
        }
    }
}