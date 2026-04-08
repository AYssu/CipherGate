package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Permission;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.PermissionService;
import com.ayssu.ciphergate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "系统权限维护接口")
public class PermissionController {
    
    private final PermissionService permissionService;
    private final UserService userService;
    
    /**
     * 获取所有权限列表
     */
    @GetMapping
    @RequirePermission("PERMISSION_LIST")
    @Operation(summary = "获取权限列表")
    public Result<List<Permission>> getAllPermissions() {
        List<Permission> permissions = permissionService.getAllPermissions();
        return Result.success(permissions);
    }
    
    /**
     * 根据ID获取权限详情
     */
    @GetMapping("/{id}")
    @RequirePermission("PERMISSION_LIST")
    @Operation(summary = "获取权限详情")
    public Result<Permission> getPermissionById(@PathVariable Long id) {
        Permission permission = permissionService.getById(id);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        return Result.success(permission);
    }
    
    /**
     * 创建权限
     */
    @PostMapping
    @RequirePermission("PERMISSION_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "PERMISSION", description = "创建权限")
    @Operation(summary = "创建权限")
    public Result<String> createPermission(@RequestBody Permission permission) {
        try {
            // 验证权限编码唯一性
            if (permissionService.getPermissionByCode(permission.getPermissionCode()) != null) {
                return Result.error("权限编码已存在");
            }
            
            boolean success = permissionService.createPermission(permission);
            if (success) {
                return Result.success("权限创建成功");
            } else {
                return Result.error("权限创建失败");
            }
        } catch (Exception e) {
            log.error("创建权限失败", e);
            return Result.error("权限创建失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新权限
     */
    @PutMapping("/{id}")
    @RequirePermission("PERMISSION_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "PERMISSION", description = "更新权限")
    @Operation(summary = "更新权限")
    public Result<String> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        try {
            permission.setId(id);
            
            // 验证权限编码唯一性（排除自己）
            Permission existingPermission = permissionService.getPermissionByCode(permission.getPermissionCode());
            if (existingPermission != null && !existingPermission.getId().equals(id)) {
                return Result.error("权限编码已存在");
            }
            
            boolean success = permissionService.updatePermission(permission);
            if (success) {
                return Result.success("权限更新成功");
            } else {
                return Result.error("权限更新失败");
            }
        } catch (Exception e) {
            log.error("更新权限失败", e);
            return Result.error("权限更新失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    @RequirePermission("PERMISSION_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "PERMISSION", description = "删除权限")
    @Operation(summary = "删除权限")
    public Result<String> deletePermission(@PathVariable Long id) {
        try {
            // 检查权限是否被角色使用
            if (permissionService.isPermissionInUse(id)) {
                return Result.error("该权限正在被角色使用，无法删除");
            }
            
            boolean success = permissionService.deletePermission(id);
            if (success) {
                return Result.success("权限删除成功");
            } else {
                return Result.error("权限删除失败");
            }
        } catch (Exception e) {
            log.error("删除权限失败", e);
            return Result.error("权限删除失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量删除权限
     */
    @DeleteMapping("/batch")
    @RequirePermission("PERMISSION_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "PERMISSION", description = "批量删除权限")
    @Operation(summary = "批量删除权限")
    public Result<String> batchDeletePermissions(@RequestBody List<Long> ids) {
        try {
            boolean success = permissionService.batchDeletePermissions(ids);
            if (success) {
                return Result.success("批量删除成功");
            } else {
                return Result.error("批量删除失败");
            }
        } catch (Exception e) {
            log.error("批量删除权限失败", e);
            return Result.error("批量删除失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取资源类型选项
     */
    @GetMapping("/resource-types")
    @RequirePermission("PERMISSION_LIST")
    @Operation(summary = "获取资源类型选项")
    public Result<List<String>> getResourceTypes() {
        List<String> resourceTypes = List.of("API", "MENU", "BUTTON", "DATA");
        return Result.success(resourceTypes);
    }
    
    /**
     * 获取HTTP方法选项
     */
    @GetMapping("/http-methods")
    @RequirePermission("PERMISSION_LIST")
    @Operation(summary = "获取HTTP方法选项")
    public Result<List<String>> getHttpMethods() {
        List<String> httpMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
        return Result.success(httpMethods);
    }
}