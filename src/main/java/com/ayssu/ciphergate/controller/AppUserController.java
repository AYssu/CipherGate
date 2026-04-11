package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.dto.ExtendMemberDaysDTO;
import com.ayssu.ciphergate.dto.MemberExpiresAtDTO;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.AppUserService;
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
import java.util.Map;

/**
 * 应用终端用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app-users")
@RequiredArgsConstructor
@Tag(name = "应用终端用户管理", description = "应用终端用户的增删改查接口")
public class AppUserController {
    
    private final AppUserService appUserService;
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
    @RequirePermission("APP_USER_LIST")
    @Operation(summary = "分页查询终端用户")
    public Result<Page<AppUser>> getAppUserList(AppUserQueryDTO queryDTO) {
        try {
            User currentUser = getCurrentUser();
            Page<AppUser> page = appUserService.getAppUserPage(queryDTO, currentUser.getId());
            return Result.success("查询成功", page);
        } catch (Exception e) {
            log.error("查询终端用户列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @RequirePermission("APP_USER_DETAIL")
    @Operation(summary = "查询终端用户详情")
    public Result<AppUser> getAppUserById(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            AppUser appUser = appUserService.getAppUserById(id, currentUser.getId());
            return Result.success("查询成功", appUser);
        } catch (Exception e) {
            log.error("查询终端用户详情失败: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    @RequirePermission("APP_USER_CREATE")
    @ActivityLog(actionType = "CREATE", actionTarget = "APP_USER", description = "创建终端用户")
    @Operation(summary = "创建终端用户")
    public Result<AppUser> createAppUser(@RequestBody AppUserDTO dto) {
        try {
            User currentUser = getCurrentUser();
            AppUser appUser = appUserService.createAppUser(dto, currentUser.getId());
            return Result.success("创建成功", appUser);
        } catch (Exception e) {
            log.error("创建终端用户失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    @RequirePermission("APP_USER_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER", description = "更新终端用户")
    @Operation(summary = "更新终端用户")
    public Result<AppUser> updateAppUser(
            @PathVariable Long id,
            @RequestBody AppUserDTO dto) {
        try {
            User currentUser = getCurrentUser();
            AppUser appUser = appUserService.updateAppUser(id, dto, currentUser.getId());
            return Result.success("更新成功", appUser);
        } catch (Exception e) {
            log.error("更新终端用户失败: id={}", id, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    @RequirePermission("APP_USER_DELETE")
    @ActivityLog(actionType = "DELETE", actionTarget = "APP_USER", description = "删除终端用户")
    @Operation(summary = "删除终端用户")
    public Result<Void> deleteAppUser(@PathVariable Long id) {
        try {
            User currentUser = getCurrentUser();
            appUserService.deleteAppUser(id, currentUser.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除终端用户失败: id={}", id, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/reset-password")
    @RequirePermission("APP_USER_RESET_PWD")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER", description = "重置终端用户密码")
    @Operation(summary = "重置用户密码")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Result.error("新密码不能为空");
            }
            
            User currentUser = getCurrentUser();
            appUserService.resetPassword(id, newPassword, currentUser.getId());
            return Result.success("密码重置成功", null);
        } catch (Exception e) {
            log.error("重置用户密码失败: id={}", id, e);
            return Result.error("重置失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/ban")
    @RequirePermission("APP_USER_BAN")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER", description = "封禁或解封终端用户")
    @Operation(summary = "封禁/解封用户")
    public Result<Void> banUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Boolean ban = (Boolean) request.get("ban");
            String reason = (String) request.get("reason");
            Long bindingId = request.get("bindingId") != null ? 
                Long.valueOf(request.get("bindingId").toString()) : null;
            
            if (ban == null) {
                return Result.error("ban参数不能为空");
            }
            
            User currentUser = getCurrentUser();
            appUserService.banUser(id, bindingId, ban, reason, currentUser.getId());
            return Result.success(ban ? "封禁成功" : "解封成功", null);
        } catch (Exception e) {
            log.error("封禁/解封用户失败: id={}", id, e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}/bindings")
    @RequirePermission("APP_USER_DETAIL")
    @Operation(summary = "获取用户绑定设备列表")
    public Result<Page<AppUserBinding>> getUserBindings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            User currentUser = getCurrentUser();
            Page<AppUserBinding> page = appUserService.getUserBindings(id, current, size, currentUser.getId());
            return Result.success("查询成功", page);
        } catch (Exception e) {
            log.error("查询用户绑定设备列表失败: userId={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{userId}/bindings/{bindingId}")
    @RequirePermission("APP_USER_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER_BINDING", description = "解绑终端用户设备")
    @Operation(summary = "解绑用户设备")
    public Result<Void> unbindDevice(
            @PathVariable Long userId,
            @PathVariable Long bindingId,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String reason = request != null ? request.get("reason") : null;
            User currentUser = getCurrentUser();
            appUserService.unbindDevice(userId, bindingId, reason, currentUser.getId());
            return Result.success("解绑成功", null);
        } catch (Exception e) {
            log.error("解绑用户设备失败: userId={}, bindingId={}", userId, bindingId, e);
            return Result.error("解绑失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/extend-member")
    @RequirePermission("APP_USER_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER", description = "延长终端用户会员")
    @Operation(summary = "延长会员（按天累加到到期时间）")
    public Result<AppUser> extendMember(
            @PathVariable Long id,
            @Valid @RequestBody ExtendMemberDaysDTO body) {
        try {
            User currentUser = getCurrentUser();
            AppUser updated = appUserService.extendMemberByDays(id, body.getDays(), currentUser.getId());
            return Result.success("延长成功", updated);
        } catch (Exception e) {
            log.error("延长会员失败: id={}", id, e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/member-expires")
    @RequirePermission("APP_USER_UPDATE")
    @ActivityLog(actionType = "UPDATE", actionTarget = "APP_USER", description = "设置终端用户会员到期")
    @Operation(summary = "设置/清空会员到期时间")
    public Result<AppUser> setMemberExpires(
            @PathVariable Long id,
            @RequestBody MemberExpiresAtDTO body) {
        try {
            User currentUser = getCurrentUser();
            AppUser updated = appUserService.setMemberExpiresAt(id,
                    body != null ? body.getMemberExpiresAt() : null,
                    currentUser.getId());
            return Result.success("保存成功", updated);
        } catch (Exception e) {
            log.error("设置会员到期失败: id={}", id, e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

}
