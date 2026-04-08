package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.entity.AppUser;
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
    @Operation(summary = "分页查询终端用户")
    public Result<Page<AppUser>> getAppUserList(AppUserQueryDTO queryDTO) {
        try {
            Page<AppUser> page = appUserService.getAppUserPage(queryDTO);
            return Result.success("查询成功", page);
        } catch (Exception e) {
            log.error("查询终端用户列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查询终端用户详情")
    public Result<AppUser> getAppUserById(@PathVariable Long id) {
        try {
            AppUser appUser = appUserService.getAppUserById(id);
            return Result.success("查询成功", appUser);
        } catch (Exception e) {
            log.error("查询终端用户详情失败: id={}", id, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping
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
}
