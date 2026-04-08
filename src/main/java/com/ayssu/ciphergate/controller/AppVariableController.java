package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.AppVariableDTO;
import com.ayssu.ciphergate.dto.AppVariableQueryDTO;
import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.AppVariableHistory;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppVariableService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 应用变量管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app-variables")
@RequiredArgsConstructor
@Tag(name = "应用变量管理", description = "应用变量的增删改查接口")
public class AppVariableController {
    
    private final AppVariableService appVariableService;
    private final UserMapper userMapper;
    
    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2User oauth2User)) {
            throw new RuntimeException("无效的认证信息");
        }

        Object idObj = oauth2User.getAttribute("id");
        String githubId = idObj != null ? idObj.toString() : null;
        
        if (githubId == null) {
            throw new RuntimeException("无法获取 GitHub 用户 ID");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getGithubId, githubId);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return user;
    }
    
    @GetMapping
    @RequirePermission("APP_VARIABLE_LIST")
    @Operation(summary = "分页查询变量")
    public Result<Page<AppVariable>> getVariableList(AppVariableQueryDTO queryDTO) {
        try {
            User currentUser = getCurrentUser();
            Page<AppVariable> page = appVariableService.getVariablePage(queryDTO, currentUser.getId());
            return Result.success("查询成功", page);
        } catch (Exception e) {
            log.error("查询变量列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @RequirePermission("APP_VARIABLE_DETAIL")
    @Operation(summary = "查询变量详情")
    public Result<AppVariable> getVariableById(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.getVariableById(id, currentUser.getId());
            return Result.success("查询成功", variable);
        } catch (Exception e) {
            log.error("查询变量详情失败: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/by-name")
    @RequirePermission("APP_VARIABLE_DETAIL")
    @Operation(summary = "根据名称查询变量")
    public Result<AppVariable> getVariableByName(
            @RequestParam Long appId,
            @RequestParam String variableName) {
        try {
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.getVariableByName(appId, variableName, currentUser.getId());
            return Result.success("查询成功", variable);
        } catch (Exception e) {
            log.error("根据名称查询变量失败: appId={}, variableName={}", appId, variableName, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    @RequirePermission("APP_VARIABLE_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "APP_VARIABLE", description = "创建应用变量")
    @Operation(summary = "创建变量")
    public Result<AppVariable> createVariable(@Valid @RequestBody AppVariableDTO dto) {
        try {
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.createVariable(dto, currentUser.getId());
            return Result.success("创建成功", variable);
        } catch (Exception e) {
            log.error("创建变量失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    @RequirePermission("APP_VARIABLE_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_VARIABLE", description = "更新应用变量")
    @Operation(summary = "更新变量")
    public Result<AppVariable> updateVariable(
            @PathVariable Long id,
            @Valid @RequestBody AppVariableDTO dto) {
        try {
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.updateVariable(id, dto, currentUser.getId());
            return Result.success("更新成功", variable);
        } catch (Exception e) {
            log.error("更新变量失败: id={}", id, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    @RequirePermission("APP_VARIABLE_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "APP_VARIABLE", description = "删除应用变量")
    @Operation(summary = "删除变量")
    public Result<Void> deleteVariable(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            appVariableService.deleteVariable(id, currentUser.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除变量失败: id={}", id, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/batch")
    @RequirePermission("APP_VARIABLE_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "APP_VARIABLE", description = "批量删除应用变量")
    @Operation(summary = "批量删除变量")
    public Result<Void> deleteVariables(@RequestBody List<Long> ids) {
        try {
            User currentUser = getCurrentUser();
            appVariableService.deleteVariables(ids, currentUser.getId());
            return Result.success("批量删除成功", null);
        } catch (Exception e) {
            log.error("批量删除变量失败: ids={}", ids, e);
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/copy")
    @RequirePermission("APP_VARIABLE_COPY")
    @ActivityLog(actionType = "CREATE", actionTarget = "APP_VARIABLE", description = "复制应用变量")
    @Operation(summary = "复制变量")
    public Result<AppVariable> copyVariable(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String newVariableName = request.get("newVariableName");
            if (newVariableName == null || newVariableName.trim().isEmpty()) {
                return Result.error("新变量名不能为空");
            }
            
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.copyVariable(id, newVariableName, currentUser.getId());
            return Result.success("复制成功", variable);
        } catch (Exception e) {
            log.error("复制变量失败: id={}", id, e);
            return Result.error("复制失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/app/{appId}")
    @RequirePermission("APP_VARIABLE_LIST")
    @Operation(summary = "获取应用的所有变量")
    public Result<Map<String, Object>> getAppVariables(
            @PathVariable Long appId) {
        try {
            User currentUser = getCurrentUser();
            Map<String, Object> variables = appVariableService.getAppVariables(appId, currentUser.getId());
            return Result.success("查询成功", variables);
        } catch (Exception e) {
            log.error("获取应用变量失败: appId={}", appId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/app/{appId}/batch")
    @RequirePermission("APP_VARIABLE_BATCH_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_VARIABLE", description = "批量更新应用变量值")
    @Operation(summary = "批量更新变量值")
    public Result<Void> batchUpdateVariables(
            @PathVariable Long appId,
            @RequestBody Map<String, Object> variables) {
        try {
            User currentUser = getCurrentUser();
            appVariableService.batchUpdateVariables(appId, variables, currentUser.getId());
            return Result.success("批量更新成功", null);
        } catch (Exception e) {
            log.error("批量更新变量失败: appId={}", appId, e);
            return Result.error("批量更新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}/history")
    @RequirePermission("APP_VARIABLE_HISTORY")
    @Operation(summary = "获取变量历史记录")
    public Result<Page<AppVariableHistory>> getVariableHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            User currentUser = getCurrentUser();
            Page<AppVariableHistory> page = appVariableService.getVariableHistory(id, current, size, currentUser.getId());
            return Result.success("查询成功", page);
        } catch (Exception e) {
            log.error("查询变量历史失败: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/app/{appId}/export")
    @RequirePermission("APP_VARIABLE_EXPORT")
    @Operation(summary = "导出变量配置")
    public Result<String> exportVariables(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "json") String format) {
        try {
            User currentUser = getCurrentUser();
            String config = appVariableService.exportVariables(appId, format, currentUser.getId());
            return Result.success("导出成功", config);
        } catch (Exception e) {
            log.error("导出变量配置失败: appId={}, format={}", appId, format, e);
            return Result.error("导出失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/app/{appId}/import")
    @RequirePermission("APP_VARIABLE_IMPORT")
    @ActivityLog(actionType = "IMPORT", actionTarget = "APP_VARIABLE", description = "导入应用变量配置")
    @Operation(summary = "导入变量配置")
    public Result<Void> importVariables(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "json") String format,
            @RequestBody Map<String, String> request) {
        try {
            String configData = request.get("configData");
            if (configData == null || configData.trim().isEmpty()) {
                return Result.error("配置数据不能为空");
            }
            
            User currentUser = getCurrentUser();
            appVariableService.importVariables(appId, configData, format, currentUser.getId());
            return Result.success("导入成功", null);
        } catch (Exception e) {
            log.error("导入变量配置失败: appId={}, format={}", appId, format, e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/validate")
    @RequirePermission("APP_VARIABLE_VALIDATE")
    @Operation(summary = "验证变量值")
    public Result<Boolean> validateVariableValue(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            User currentUser = getCurrentUser();
            AppVariable variable = appVariableService.getVariableById(id, currentUser.getId());
            boolean valid = appVariableService.validateVariableValue(variable, value);
            return Result.success("验证完成", valid);
        } catch (Exception e) {
            log.error("验证变量值失败: id={}", id, e);
            return Result.error("验证失败: " + e.getMessage());
        }
    }
}