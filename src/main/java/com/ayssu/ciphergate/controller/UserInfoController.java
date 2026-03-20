package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
     * 获取当前用户信息（包含角色、权限、菜单）
     */
    @GetMapping("/info")
    public Result<User> getCurrentUserInfo(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return Result.error("用户未登录");
        }
        
        // 获取完整的用户信息
        User userInfo = userService.getUserWithRolesPermissionsAndMenus(sessionUser.getId());
        
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
    public Result<User> getCurrentUserProfile(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error("用户未登录");
        }
        
        // 清除敏感信息
        user.setAccessToken(null);
        
        return Result.success(user);
    }
}