package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.LicenseBatchCreateDTO;
import com.ayssu.ciphergate.dto.LicenseKeyDTO;
import com.ayssu.ciphergate.dto.LicenseKeyQueryDTO;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卡密管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
@Tag(name = "卡密管理", description = "卡密管理相关接口")
public class LicenseKeyController {
    
    private final LicenseKeyService licenseKeyService;
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
     * 分页查询卡密列表
     */
    @GetMapping
    @RequirePermission("LICENSE_LIST")
    @ActivityLog(actionType = "VIEW", actionTarget = "LICENSE", description = "查看卡密列表")
    @Operation(summary = "查询卡密列表")
    public Result<Page<LicenseKey>> getLicenseKeys(LicenseKeyQueryDTO queryDTO) {
        try {
            User currentUser = getCurrentUser();
            Page<LicenseKey> page = licenseKeyService.getLicenseKeyPage(queryDTO, currentUser.getId());
            return Result.success(page);
        } catch (Exception e) {
            log.error("获取卡密列表失败", e);
            return Result.error("获取卡密列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取卡密详情
     */
    @GetMapping("/{id}")
    @RequirePermission("LICENSE_DETAIL")
    @ActivityLog(actionType = "VIEW", actionTarget = "LICENSE", description = "查看卡密详情")
    @Operation(summary = "查询卡密详情")
    public Result<LicenseKey> getLicenseKeyById(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            LicenseKey licenseKey = licenseKeyService.getLicenseKeyById(id, currentUser.getId());
            return Result.success(licenseKey);
        } catch (Exception e) {
            log.error("获取卡密详情失败", e);
            return Result.error("获取卡密详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建单个卡密
     */
    @PostMapping
    @RequirePermission("LICENSE_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "LICENSE", description = "创建卡密")
    @Operation(summary = "创建卡密")
    public Result<LicenseKey> createLicenseKey(@RequestBody LicenseKeyDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseKey licenseKey = licenseKeyService.createLicenseKey(dto, currentUser.getId());
            return Result.success("卡密创建成功", licenseKey);
        } catch (Exception e) {
            log.error("创建卡密失败", e);
            return Result.error("创建卡密失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量生成卡密
     */
    @PostMapping("/batch")
    @RequirePermission("LICENSE_BATCH_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "LICENSE", description = "批量生成卡密")
    @Operation(summary = "批量生成卡密")
    public Result<List<LicenseKey>> batchCreateLicenseKeys(@RequestBody LicenseBatchCreateDTO dto) {
        try {
            User currentUser = getCurrentUser();
            List<LicenseKey> licenseKeys = licenseKeyService.batchCreateLicenseKeys(dto, currentUser.getId());
            return Result.success("批量生成卡密成功", licenseKeys);
        } catch (Exception e) {
            log.error("批量生成卡密失败", e);
            return Result.error("批量生成卡密失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新卡密
     */
    @PutMapping("/{id}")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "更新卡密")
    @Operation(summary = "更新卡密")
    public Result<LicenseKey> updateLicenseKey(
            @PathVariable Long id,
            @RequestBody LicenseKeyDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseKey licenseKey = licenseKeyService.updateLicenseKey(id, dto, currentUser.getId());
            return Result.success("卡密更新成功", licenseKey);
        } catch (Exception e) {
            log.error("更新卡密失败", e);
            return Result.error("更新卡密失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除卡密
     */
    @DeleteMapping("/{id}")
    @RequirePermission("LICENSE_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "LICENSE", description = "删除卡密")
    @Operation(summary = "删除卡密")
    public Result<String> deleteLicenseKey(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            licenseKeyService.deleteLicenseKey(id, currentUser.getId());
            return Result.success("卡密删除成功");
        } catch (Exception e) {
            log.error("删除卡密失败", e);
            return Result.error("删除卡密失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新卡密状态
     */
    @PutMapping("/{id}/status")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "更新卡密状态")
    @Operation(summary = "更新卡密状态")
    public Result<String> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            User currentUser = getCurrentUser();
            licenseKeyService.updateStatus(id, status, currentUser.getId());
            String statusDesc = status == 1 ? "未使用" : (status == 2 ? "使用中" : (status == 3 ? "已过期" : "已禁用"));
            return Result.success("卡密状态已更新为: " + statusDesc);
        } catch (Exception e) {
            log.error("更新卡密状态失败", e);
            return Result.error("更新卡密状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出卡密
     */
    @GetMapping("/export")
    @RequirePermission("LICENSE_EXPORT")
    @ActivityLog(actionType = "EXPORT", actionTarget = "LICENSE", description = "导出卡密")
    @Operation(summary = "导出卡密")
    public Result<List<LicenseKey>> exportLicenseKeys(LicenseKeyQueryDTO queryDTO) {
        try {
            User currentUser = getCurrentUser();
            List<LicenseKey> licenseKeys = licenseKeyService.exportLicenseKeys(queryDTO, currentUser.getId());
            return Result.success(licenseKeys);
        } catch (Exception e) {
            log.error("导出卡密失败", e);
            return Result.error("导出卡密失败: " + e.getMessage());
        }
    }
}
