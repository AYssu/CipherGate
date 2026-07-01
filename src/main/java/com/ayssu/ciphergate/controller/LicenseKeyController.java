package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.LicenseBatchAddTimeDTO;
import com.ayssu.ciphergate.dto.LicenseBatchAddTimeResultDTO;
import com.ayssu.ciphergate.dto.LicenseBatchCreateDTO;
import com.ayssu.ciphergate.dto.LicenseBatchDeleteDTO;
import com.ayssu.ciphergate.dto.LicenseBatchOperateResultDTO;
import com.ayssu.ciphergate.dto.LicenseBatchSetUnbindLimitDTO;
import com.ayssu.ciphergate.dto.LicenseBatchSetUseLimitDTO;
import com.ayssu.ciphergate.dto.LicenseBatchSetUseTimeDTO;
import com.ayssu.ciphergate.dto.LicenseBatchStatusDTO;
import com.ayssu.ciphergate.dto.LicenseBatchUnbindDTO;
import com.ayssu.ciphergate.dto.LicenseKeyDTO;
import com.ayssu.ciphergate.dto.LicenseImportResult;
import com.ayssu.ciphergate.dto.LicenseKeyQueryDTO;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 卡密管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
@Validated
@Tag(name = "卡密管理", description = "卡密管理相关接口")
public class LicenseKeyController {
    
    private final LicenseKeyService licenseKeyService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取当前登录用户（兼容 OAuth2 和密码登录）
     */
    private User getCurrentUser() {
        User user = AuthUtils.getCurrentUser();
        if (user != null) return user;

        Authentication authentication = AuthUtils.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String githubId = oauth2User.getAttribute("id").toString();
            user = userService.getUserByGithubId(githubId);
            if (user != null) return user;
        }

        throw new SecurityException("用户未登录");
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
     * 批量加时（仅已激活且有到期时间的卡密；未激活返回「该卡密未激活」等明细）
     */
    @PostMapping("/batch-add-time")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量加时")
    @Operation(summary = "卡密批量加时")
    public Result<LicenseBatchAddTimeResultDTO> batchAddExpiryTime(@Valid @RequestBody LicenseBatchAddTimeDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchAddTimeResultDTO r = licenseKeyService.batchAddExpiryTime(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量加时失败", e);
            return Result.error("卡密批量加时失败: " + e.getMessage());
        }
    }

    /**
     * 批量扣时（仅已激活且有到期时间的卡密；未激活返回「该卡密未激活」等明细）
     */
    @PostMapping("/batch-subtract-time")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量扣时")
    @Operation(summary = "卡密批量扣时")
    public Result<LicenseBatchAddTimeResultDTO> batchSubtractExpiryTime(@Valid @RequestBody LicenseBatchAddTimeDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchAddTimeResultDTO r = licenseKeyService.batchSubtractExpiryTime(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量扣时失败", e);
            return Result.error("卡密批量扣时失败: " + e.getMessage());
        }
    }

    /**
     * 批量封禁卡密
     */
    @PostMapping("/batch-status")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量更新状态")
    @Operation(summary = "卡密批量更新状态")
    public Result<LicenseBatchOperateResultDTO> batchUpdateStatus(@Valid @RequestBody LicenseBatchStatusDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchUpdateStatus(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量更新状态失败", e);
            return Result.error("卡密批量更新状态失败: " + e.getMessage());
        }
    }

    /**
     * 批量解绑
     */
    @PostMapping("/batch-unbind")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量解绑")
    @Operation(summary = "卡密批量解绑设备/IP")
    public Result<LicenseBatchOperateResultDTO> batchUnbind(@Valid @RequestBody LicenseBatchUnbindDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchUnbind(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量解绑失败", e);
            return Result.error("卡密批量解绑失败: " + e.getMessage());
        }
    }

    /**
     * 批量设置使用次数限制
     */
    @PostMapping("/batch-use-limit")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量设置使用次数限制")
    @Operation(summary = "卡密批量设置使用次数限制")
    public Result<LicenseBatchOperateResultDTO> batchSetUseLimit(@Valid @RequestBody LicenseBatchSetUseLimitDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchSetUseLimit(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量设置使用次数限制失败", e);
            return Result.error("卡密批量设置使用次数限制失败: " + e.getMessage());
        }
    }

    /**
     * 批量设置解绑次数限制
     */
    @PostMapping("/batch-unbind-limit")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量设置解绑次数限制")
    @Operation(summary = "卡密批量设置解绑次数限制")
    public Result<LicenseBatchOperateResultDTO> batchSetUnbindLimit(@Valid @RequestBody LicenseBatchSetUnbindLimitDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchSetUnbindLimit(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量设置解绑次数限制失败", e);
            return Result.error("卡密批量设置解绑次数限制失败: " + e.getMessage());
        }
    }

    /**
     * 批量设置使用时间段限制
     */
    @PostMapping("/batch-use-time")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密批量设置使用时间段限制")
    @Operation(summary = "卡密批量设置使用时间段限制")
    public Result<LicenseBatchOperateResultDTO> batchSetUseTimeRange(@Valid @RequestBody LicenseBatchSetUseTimeDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchSetUseTimeRange(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量设置使用时间段失败", e);
            return Result.error("卡密批量设置使用时间段失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除卡密
     */
    @PostMapping("/batch-delete")
    @RequirePermission("LICENSE_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "LICENSE", description = "卡密批量删除")
    @Operation(summary = "卡密批量删除")
    public Result<LicenseBatchOperateResultDTO> batchDelete(@Valid @RequestBody LicenseBatchDeleteDTO dto) {
        try {
            User currentUser = getCurrentUser();
            LicenseBatchOperateResultDTO r = licenseKeyService.batchDelete(dto, currentUser.getId());
            return Result.success("处理完成", r);
        } catch (Exception e) {
            log.error("卡密批量删除失败", e);
            return Result.error("卡密批量删除失败: " + e.getMessage());
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
            String statusDesc = status == 1 ? "未使用" : (status == 2 ? "使用中" : (status == 3 ? "已到期" : "已禁用"));
            return Result.success("卡密状态已更新为: " + statusDesc);
        } catch (Exception e) {
            log.error("更新卡密状态失败", e);
            return Result.error("更新卡密状态失败: " + e.getMessage());
        }
    }

    /**
     * 解绑设备（下次卡密登录可绑定新设备）
     */
    @PostMapping("/{id}/unbind-device")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密解绑设备")
    @Operation(summary = "卡密解绑设备")
    public Result<LicenseKey> unbindDevice(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            LicenseKey key = licenseKeyService.unbindDevice(id, currentUser.getId());
            return Result.success("已解绑设备", key);
        } catch (Exception e) {
            log.error("卡密解绑设备失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解绑 IP
     */
    @PostMapping("/{id}/unbind-ip")
    @RequirePermission("LICENSE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "LICENSE", description = "卡密解绑IP")
    @Operation(summary = "卡密解绑IP")
    public Result<LicenseKey> unbindIp(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            LicenseKey key = licenseKeyService.unbindIp(id, currentUser.getId());
            return Result.success("已解绑IP", key);
        } catch (Exception e) {
            log.error("卡密解绑IP失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 导出卡密（Excel .xlsx，Hutool POI）
     */
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("LICENSE_EXPORT")
    @ActivityLog(actionType = "EXPORT", actionTarget = "LICENSE", description = "导出卡密")
    @Operation(summary = "导出卡密为Excel")
    public void exportLicenseKeys(LicenseKeyQueryDTO queryDTO, HttpServletResponse response) {
        try {
            User currentUser = getCurrentUser();
            byte[] bytes = licenseKeyService.exportLicenseKeysExcel(queryDTO, currentUser.getId());
            String name = "卡密导出_" + LocalDate.now() + ".xlsx";
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("导出卡密失败", e);
            try {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getOutputStream(),
                        Result.error("导出卡密失败: " + e.getMessage()));
            } catch (Exception ex) {
                log.error("写入导出错误响应失败", ex);
            }
        }
    }

    /**
     * 下载卡密导入模板
     */
    @GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("LICENSE_CREATE")
    @Operation(summary = "下载卡密导入模板")
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            byte[] bytes = licenseKeyService.generateImportTemplate();
            String name = "卡密导入模板_" + LocalDate.now() + ".xlsx";
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("下载导入模板失败", e);
            try {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getOutputStream(),
                        Result.error("下载模板失败: " + e.getMessage()));
            } catch (Exception ex) {
                log.error("写入模板错误响应失败", ex);
            }
        }
    }

    /**
     * 批量导入卡密
     */
    @PostMapping("/import")
    @RequirePermission("LICENSE_CREATE")
    @ActivityLog(actionType = "IMPORT", actionTarget = "LICENSE", description = "批量导入卡密")
    @Operation(summary = "从Excel批量导入卡密")
    public Result<LicenseImportResult> importLicenseKeys(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long appId) {
        try {
            User currentUser = getCurrentUser();
            if (file == null || file.isEmpty()) {
                return Result.error("请选择文件");
            }
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
                return Result.error("仅支持 .xlsx 格式");
            }
            byte[] bytes = file.getBytes();
            LicenseImportResult result = licenseKeyService.importLicenseKeys(bytes, appId, currentUser.getId());
            String msg = "导入完成: 成功 " + result.getSuccessCount() + " 条";
            if (result.getFailCount() > 0) {
                msg += "，失败 " + result.getFailCount() + " 条";
            }
            return Result.success(msg, result);
        } catch (Exception e) {
            log.error("导入卡密失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }
}
