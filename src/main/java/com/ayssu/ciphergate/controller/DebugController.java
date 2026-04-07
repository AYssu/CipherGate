package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调试控制器 - 用于查看当前用户的权限信息
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {
    
    /**
     * 查看当前用户的权限信息
     */
    @GetMapping("/authorities")
    public Result<Map<String, Object>> getAuthorities(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        if (authentication == null) {
            result.put("authenticated", false);
            result.put("message", "未登录");
            return Result.success(result);
        }
        
        result.put("authenticated", true);
        result.put("principal", authentication.getPrincipal().getClass().getName());
        
        // 获取权限列表
        result.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        
        // 如果是 OAuth2User，获取属性
        if (authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            result.put("username", oauth2User.getAttribute("login"));
            result.put("name", oauth2User.getAttribute("name"));
        }
        
        return Result.success(result);
    }
}
