package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "系统用户维护接口")
public class UserController {
    
    private final UserService userService;
    private final RoleService roleService;
    
    /**
     * 获取用户列表
     */
    @GetMapping
    @RequirePermission("USER_LIST")
    @ActivityLog(actionType = "VIEW", actionTarget = "USER_MANAGEMENT", description = "查看用户列表")
    @Operation(summary = "获取用户列表")
    public Result<List<User>> getUsers() {
        try {
            List<User> users = userService.getAllUsersWithRoles();
            return Result.success(users);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return Result.error("获取用户列表失败");
        }
    }
    
    /**
     * 根据ID获取用户详情
     */
    @GetMapping("/{id}")
    @RequirePermission("USER_DETAIL")
    @Operation(summary = "获取用户详情")
    public Result<User> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserWithRolesPermissionsAndMenus(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            // 清除敏感信息
            user.setAccessToken(null);
            return Result.success(user);
        } catch (Exception e) {
            log.error("获取用户详情失败", e);
            return Result.error("获取用户详情失败");
        }
    }
    
    /**
     * 更新用户状态和角色
     */
    @PutMapping("/{id}")
    @RequirePermission("USER_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "USER_MANAGEMENT", description = "更新用户信息")
    @Operation(summary = "更新用户状态与角色")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updateData) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 更新用户状态
            if (updateData.containsKey("status")) {
                Integer status = (Integer) updateData.get("status");
                user.setStatus(status);
                userService.updateById(user);
            }
            
            // 更新用户角色
            if (updateData.containsKey("roleIds")) {
                @SuppressWarnings("unchecked")
                List<Integer> roleIdInts = (List<Integer>) updateData.get("roleIds");
                List<Long> roleIds = roleIdInts.stream().map(Integer::longValue).toList();
                
                // 先清除现有角色，再分配新角色
                roleService.clearUserRoles(id);
                if (!roleIds.isEmpty()) {
                    roleService.assignRolesToUser(id, roleIds);
                }
            }
            
            return Result.success("用户更新成功");
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return Result.error("更新用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @RequirePermission("USER_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "USER_MANAGEMENT", description = "删除用户")
    @Operation(summary = "删除用户")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 检查是否是超级管理员
            if (userService.hasRole(id, "SUPER_ADMIN")) {
                return Result.error("不能删除超级管理员");
            }
            
            boolean success = userService.removeById(id);
            if (success) {
                return Result.success("用户删除成功");
            } else {
                return Result.error("用户删除失败");
            }
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return Result.error("删除用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 启用/禁用用户
     */
    @PutMapping("/{id}/status")
    @RequirePermission("USER_UPDATE")
    @Operation(summary = "启用或禁用用户")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 检查是否是超级管理员
            if (userService.hasRole(id, "SUPER_ADMIN") && status == 0) {
                return Result.error("不能禁用超级管理员");
            }
            
            user.setStatus(status);
            boolean success = userService.updateById(user);
            if (success) {
                return Result.success(status == 1 ? "用户已启用" : "用户已禁用");
            } else {
                return Result.error("更新用户状态失败");
            }
        } catch (Exception e) {
            log.error("更新用户状态失败", e);
            return Result.error("更新用户状态失败: " + e.getMessage());
        }
    }
}