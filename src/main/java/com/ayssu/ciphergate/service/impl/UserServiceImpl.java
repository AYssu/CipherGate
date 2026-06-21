package com.ayssu.ciphergate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.entity.Permission;
import com.ayssu.ciphergate.entity.Role;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.mapper.PermissionMapper;
import com.ayssu.ciphergate.mapper.RoleMapper;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.service.RoleService;
import com.ayssu.ciphergate.service.MenuService;
import com.ayssu.ciphergate.service.UserMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    private final UserMapper userMapper;
    
    @Autowired
    private PermissionMapper permissionMapper;
    
    @Autowired
    private RoleMapper roleMapper;
    
    @Autowired
    private RoleService roleService;
    
    @Autowired
    private MenuService menuService;
    
    @Lazy
    @Autowired
    private UserMembershipService userMembershipService;
    
    @Override
    public User getUserByGithubId(String githubId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("github_id", githubId);
        return userMapper.selectOne(queryWrapper);
    }
    
    @Override
    public User createOrUpdateUser(String githubId, String login, String name, String email, String avatarUrl, String accessToken) {
        User existingUser = getUserByGithubId(githubId);
        
        if (existingUser != null) {
            // 更新现有用户
            existingUser.setLogin(login);
            existingUser.setName(name);
            existingUser.setEmail(email);
            existingUser.setAvatarUrl(avatarUrl);
            existingUser.setAccessToken(accessToken);
            existingUser.setUpdatedAt(LocalDateTime.now());
            updateById(existingUser);
            return existingUser;
        } else {
            // 创建新用户
            User newUser = new User();
            newUser.setGithubId(githubId);
            newUser.setLogin(login);
            newUser.setName(name);
            newUser.setEmail(email);
            newUser.setAvatarUrl(avatarUrl);
            newUser.setAccessToken(accessToken);
            newUser.setStatus(1); // 默认启用
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            save(newUser);
            
            // 初始化新用户会员配置
            try {
                userMembershipService.initMembershipForUser(newUser.getId());
            } catch (Exception e) {
                log.warn("初始化用户会员配置失败: {}", e.getMessage());
            }

;
            
            return newUser;
        }
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        User user = getById(userId);
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            updateById(user);
        }
    }
    
    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        List<Permission> permissions = permissionMapper.selectPermissionsByUserId(userId);
        return permissions.stream().anyMatch(p -> p.getPermissionCode().equals(permissionCode));
    }
    
    @Override
    public boolean hasRole(Long userId, String roleCode) {
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        return roles.stream().anyMatch(r -> r.getRoleCode().equals(roleCode));
    }
    
    @Override
    public User getUserWithRolesAndPermissions(Long userId) {
        User user = getById(userId);
        if (user != null) {
            user.setRoles(roleMapper.selectRolesByUserId(userId));
            user.setPermissions(permissionMapper.selectPermissionsByUserId(userId));
        }
        return user;
    }
    
    @Override
    public User getUserWithRolesPermissionsAndMenus(Long userId) {
        User user = getById(userId);
        if (user != null) {
            user.setRoles(roleMapper.selectRolesByUserId(userId));
            user.setPermissions(permissionMapper.selectPermissionsByUserId(userId));
            user.setMenus(menuService.getUserMenuTree(userId));
        }
        return user;
    }
    
    @Override
    public List<User> getAllUsersWithRoles() {
        List<User> users = list();
        for (User user : users) {
            user.setRoles(roleMapper.selectRolesByUserId(user.getId()));
            // 清除敏感信息
            user.setAccessToken(null);
        }
        return users;
    }
    
    @Override
    public void assignDefaultRole(Long userId) {
        // 检查是否是第一个用户
        long userCount = count();
        
        if (userCount == 1) {
            // 第一个用户分配超级管理员角色
            Role superAdminRole = roleService.getRoleByCode("SUPER_ADMIN");
            if (superAdminRole != null) {
                roleService.assignRolesToUser(userId, List.of(superAdminRole.getId()));
                log.info("为第一个用户 [{}] 分配超级管理员角色", userId);
            }
        } else {
            // 其他用户分配普通用户角色
            Role userRole = roleService.getRoleByCode("USER");
            if (userRole != null) {
                roleService.assignRolesToUser(userId, List.of(userRole.getId()));
                log.info("为用户 [{}] 分配普通用户角色", userId);
            }
        }
    }
    
    @Override
    public User findOrCreateUser(OAuth2User oauth2User) {
        log.info("=== 处理用户信息 ===");
        
        // 获取 GitHub ID
        Object idObj = oauth2User.getAttribute("id");
        String githubId = idObj != null ? idObj.toString() : null;
        
        log.info("GitHub ID: {} (类型: {})", 
            githubId, 
            idObj != null ? idObj.getClass().getSimpleName() : "null");
        
        if (githubId == null) {
            log.error("无法获取 GitHub ID，OAuth2User 属性: {}", oauth2User.getAttributes());
            throw new RuntimeException("无法获取 GitHub 用户 ID");
        }
        
        // 查找现有用户
        User existingUser = getUserByGithubId(githubId);
        
        if (existingUser != null) {
            log.info("找到现有用户: {}", existingUser);
            
            // 更新最后登录时间和用户信息
            existingUser.setLastLoginAt(LocalDateTime.now());
            existingUser.setName(getStringAttribute(oauth2User, "name"));
            existingUser.setEmail(getStringAttribute(oauth2User, "email"));
            existingUser.setAvatarUrl(getStringAttribute(oauth2User, "avatar_url"));
            existingUser.setUpdatedAt(LocalDateTime.now());
            
            log.info("更新用户信息: name={}, email={}, avatar_url={}", 
                existingUser.getName(), 
                existingUser.getEmail(), 
                existingUser.getAvatarUrl());
            
            updateById(existingUser);
            return existingUser;
        } else {
            log.info("创建新用户");
            
            // 创建新用户
            User newUser = new User();
            newUser.setGithubId(githubId);
            newUser.setLogin(getStringAttribute(oauth2User, "login"));
            newUser.setName(getStringAttribute(oauth2User, "name"));
            newUser.setEmail(getStringAttribute(oauth2User, "email"));
            newUser.setAvatarUrl(getStringAttribute(oauth2User, "avatar_url"));
            newUser.setStatus(1); // 默认启用
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            newUser.setLastLoginAt(LocalDateTime.now());
            
            log.info("新用户信息: githubId={}, login={}, name={}, email={}, avatar_url={}", 
                newUser.getGithubId(), 
                newUser.getLogin(), 
                newUser.getName(), 
                newUser.getEmail(), 
                newUser.getAvatarUrl());
            
            save(newUser);
            log.info("用户创建成功，ID: {}", newUser.getId());
            
            // 初始化新用户会员配置
            try {
                userMembershipService.initMembershipForUser(newUser.getId());
            } catch (Exception e) {
                log.warn("初始化用户会员配置失败: {}", e.getMessage());
            }
            
            // 为新用户分配默认角色
            assignDefaultRole(newUser.getId());
            
            return newUser;
        }
    }
    
    @Override
    public User findByGithubId(String githubId) {
        User user = getUserByGithubId(githubId);
        log.info("根据 GitHub ID [{}] 查找用户: {}", githubId, user);
        return user;
    }

    @Override
    public User findByLogin(String login) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("login", login);
        User user = userMapper.selectOne(queryWrapper);
        // 兼容老用户：检查并初始化会员配置
        if (user != null) {
            try {
                userMembershipService.initMembershipForUser(user.getId());
            } catch (Exception e) {
                log.debug("初始化用户会员配置跳过: {}", e.getMessage());
            }
        }
        return user;
    }
    
    private String getStringAttribute(OAuth2User oauth2User, String attributeName) {
        Object value = oauth2User.getAttribute(attributeName);
        String result = value != null ? value.toString() : null;
        log.debug("属性 [{}]: {} -> {}", 
            attributeName, 
            value, 
            result);
        return result;
    }
}