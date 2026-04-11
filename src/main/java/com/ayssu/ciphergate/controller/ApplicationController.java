package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.ApplicationDTO;
import com.ayssu.ciphergate.dto.ApplicationQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.ApplicationService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 应用管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "应用管理", description = "应用的增删改查、密钥与加密配置管理接口")
public class ApplicationController {
    
    private final ApplicationService applicationService;
    private final SecurityUtils securityUtils;
    private final UserService userService;
    
    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("用户未登录");
        }
        
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        User user = userService.getUserByGithubId(githubId);
        
        if (user == null) {
            throw new SecurityException("用户不存在");
        }
        
        return user;
    }
    
    /**
     * 分页查询应用列表
     */
    @GetMapping
    @RequirePermission("APP_LIST")
    @ActivityLog(actionType = "VIEW", actionTarget = "APPLICATION", description = "查看应用列表")
    @Operation(summary = "分页查询应用列表")
    public Result<Page<Application>> getApplications(ApplicationQueryDTO queryDTO) {
        try {
            User currentUser = getCurrentUser();
            
            // 非管理员只能查看自己创建的应用（管理员含 ADMIN / SUPER_ADMIN）
            if (!securityUtils.isAdmin(currentUser.getId())) {
                queryDTO.setOwnerId(currentUser.getId());
            }
            
            Page<Application> page = applicationService.getApplicationPage(queryDTO);
            return Result.success(page);
        } catch (Exception e) {
            log.error("获取应用列表失败", e);
            return Result.error("获取应用列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用详情
     */
    @GetMapping("/{id}")
    @RequirePermission("APP_DETAIL")
    @ActivityLog(actionType = "VIEW", actionTarget = "APPLICATION", description = "查看应用详情")
    @Operation(summary = "查询应用详情")
    public Result<Application> getApplicationById(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            Application application = applicationService.getApplicationById(id, currentUser.getId());
            return Result.success(application);
        } catch (Exception e) {
            log.error("获取应用详情失败", e);
            return Result.error("获取应用详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建应用
     */
    @PostMapping
    @RequirePermission("APP_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "APPLICATION", description = "创建应用")
    @Operation(summary = "创建应用")
    public Result<Application> createApplication(@RequestBody ApplicationDTO dto) {
        try {
            User currentUser = getCurrentUser();
            Application application = applicationService.createApplication(dto, currentUser.getId());
            return Result.success("应用创建成功", application);
        } catch (Exception e) {
            log.error("创建应用失败", e);
            return Result.error("创建应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新应用
     */
    @PutMapping("/{id}")
    @RequirePermission("APP_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APPLICATION", description = "更新应用")
    @Operation(summary = "更新应用")
    public Result<Application> updateApplication(
            @PathVariable Long id,
            @RequestBody ApplicationDTO dto) {
        try {
            User currentUser = getCurrentUser();
            Application application = applicationService.updateApplication(id, dto, currentUser.getId());
            return Result.success("应用更新成功", application);
        } catch (Exception e) {
            log.error("更新应用失败", e);
            return Result.error("更新应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除应用
     */
    @DeleteMapping("/{id}")
    @RequirePermission("APP_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "APPLICATION", description = "删除应用")
    @Operation(summary = "删除应用")
    public Result<String> deleteApplication(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            applicationService.deleteApplication(id, currentUser.getId());
            return Result.success("应用删除成功");
        } catch (Exception e) {
            log.error("删除应用失败", e);
            return Result.error("删除应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成应用密钥
     */
    @PostMapping("/generate-keys")
    @RequirePermission("APP_CREATE")
    @Operation(summary = "生成应用密钥")
    public Result<Map<String, String>> generateAppKeys() {
        try {
            Map<String, String> keys = applicationService.generateAppKeys();
            return Result.success(keys);
        } catch (Exception e) {
            log.error("生成应用密钥失败", e);
            return Result.error("生成应用密钥失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置应用密钥
     */
    @PostMapping("/{id}/reset-keys")
    @RequirePermission("APP_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APPLICATION", description = "重置应用密钥")
    @Operation(summary = "重置应用密钥")
    public Result<Map<String, String>> resetAppKeys(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            Map<String, String> keys = applicationService.resetAppKeys(id, currentUser.getId());
            return Result.success("应用密钥重置成功", keys);
        } catch (Exception e) {
            log.error("重置应用密钥失败", e);
            return Result.error("重置应用密钥失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新应用状态
    */
    @PutMapping("/{id}/status")
    @RequirePermission("APP_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APPLICATION", description = "更新应用状态")
    @Operation(summary = "更新应用状态")
    public Result<String> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            User currentUser = getCurrentUser();
            applicationService.updateStatus(id, status, currentUser.getId());
            String statusDesc = status == 1 ? "正常" : (status == 2 ? "维护" : "停用");
            return Result.success("应用状态已更新为: " + statusDesc);
        } catch (Exception e) {
            log.error("更新应用状态失败", e);
            return Result.error("更新应用状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用统计信息
     */
    @GetMapping("/{id}/stats")
    @RequirePermission("APP_DETAIL")
    @Operation(summary = "获取应用统计信息")
    public Result<Map<String, Object>> getApplicationStats(@PathVariable Long id) {
        try {
            Map<String, Object> stats = applicationService.getApplicationStats(id);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取应用统计信息失败", e);
            return Result.error("获取应用统计信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/encryption-config")
    @RequirePermission("APP_DETAIL")
    @Operation(summary = "获取应用加密配置")
    public Result<Map<String, Object>> getEncryptionConfig(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            Map<String, Object> cfg = applicationService.getEncryptionConfig(id, currentUser.getId());
            return Result.success(cfg == null ? new LinkedHashMap<>() : cfg);
        } catch (Exception e) {
            log.error("获取加密配置失败", e);
            return Result.error("获取加密配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/encryption-config")
    @RequirePermission("APP_UPDATE")
    @Operation(summary = "更新应用加密配置")
    public Result<String> updateEncryptionConfig(@PathVariable Long id, @RequestBody Map<String, Object> encryptionConfig) {
        try {
            User currentUser = getCurrentUser();
            applicationService.updateEncryptionConfig(id, encryptionConfig, currentUser.getId());
            return Result.success("加密配置更新成功", "OK");
        } catch (Exception e) {
            log.error("更新加密配置失败", e);
            return Result.error("更新加密配置失败: " + e.getMessage());
        }
    }
}
