package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoController {
    
    private final UserService userService;
    
    /**
     * 检查登录状态（无需权限）
     */
    @GetMapping("/status")
    public Result<User> checkLoginStatus(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error("未登录");
        }
        
        // 清除敏感信息
        user.setAccessToken(null);
        
        return Result.success(user);
    }
    
    /**
     * 获取当前用户信息（包含角色、权限、菜单）
     */
    @GetMapping("/info")
    @RequirePermission("PROFILE_VIEW")
    public Result<User> getCurrentUserInfo(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return Result.error("用户未登录");
        }
        
        log.info("获取用户信息，用户ID: {}", sessionUser.getId());
        
        // 获取完整的用户信息
        User userInfo = userService.getUserWithRolesPermissionsAndMenus(sessionUser.getId());
        
        // 调试日志
        if (userInfo != null) {
            log.info("用户角色数量: {}", userInfo.getRoles() != null ? userInfo.getRoles().size() : 0);
            log.info("用户菜单数量: {}", userInfo.getMenus() != null ? userInfo.getMenus().size() : 0);
            if (userInfo.getMenus() != null) {
                for (var menu : userInfo.getMenus()) {
                    log.info("菜单: {} ({}), 父ID: {}", menu.getMenuName(), menu.getMenuCode(), menu.getParentId());
                }
            }
        }
        
        // 清除敏感信息
        if (userInfo != null) {
            userInfo.setAccessToken(null);
        }
        
        return Result.success(userInfo);
    }
    
    /**
     * 获取当前用户基本信息
     */
    @GetMapping("/profile")
    @RequirePermission("PROFILE_VIEW")
    public Result<User> getCurrentUserProfile(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error("用户未登录");
        }
        
        // 清除敏感信息
        user.setAccessToken(null);
        
        return Result.success(user);
    }
    
    /**
     * 更新当前用户基本信息
     */
    @PutMapping("/profile")
    @RequirePermission("PROFILE_UPDATE")
    public Result<User> updateCurrentUserProfile(@RequestBody User updateUser, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return Result.error("用户未登录");
        }
        
        log.info("更新用户信息，用户ID: {}", sessionUser.getId());
        
        // 只允许更新部分字段
        User existingUser = userService.getById(sessionUser.getId());
        if (existingUser == null) {
            return Result.error("用户不存在");
        }
        
        // 只更新允许的字段
        if (updateUser.getName() != null) {
            existingUser.setName(updateUser.getName());
        }
        if (updateUser.getEmail() != null) {
            existingUser.setEmail(updateUser.getEmail());
        }
        
        boolean updated = userService.updateById(existingUser);
        if (updated) {
            // 更新session中的用户信息
            session.setAttribute("user", existingUser);
            // 清除敏感信息
            existingUser.setAccessToken(null);
            return Result.success(existingUser);
        } else {
            return Result.error("更新失败");
        }
    }
}