package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 自定义 OAuth2 用户服务，用于加载用户角色权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    
    private final UserService userService;
    
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 调用父类方法获取 OAuth2User
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        // 获取或创建用户
        User user = userService.findOrCreateUser(oauth2User);
        
        // 重新加载用户信息，包含角色和权限
        User userWithRoles = userService.getUserWithRolesAndPermissions(user.getId());
        if (userWithRoles.getStatus() == null || userWithRoles.getStatus() != 1) {
            throw new OAuth2AuthenticationException(new OAuth2Error("user_disabled"), "账号已被禁用");
        }
        
        // 构建用户权限
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // 添加角色权限
        if (userWithRoles.getRoles() != null && !userWithRoles.getRoles().isEmpty()) {
            for (Role role : userWithRoles.getRoles()) {
                // 添加角色权限（Spring Security 要求角色以 ROLE_ 开头）
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
                log.info("为用户 {} 添加角色权限: ROLE_{}", userWithRoles.getLogin(), role.getRoleCode());
            }
        } else {
            // 如果没有角色，默认添加普通用户角色
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            log.warn("用户 {} 没有角色，添加默认角色: ROLE_USER", userWithRoles.getLogin());
        }
        
        // 创建新的 OAuth2User，包含权限信息
        return new DefaultOAuth2User(
            authorities,
            oauth2User.getAttributes(),
            "login" // GitHub 用户的唯一标识字段
        );
    }
}
