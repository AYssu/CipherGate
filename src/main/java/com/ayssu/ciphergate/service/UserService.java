package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ayssu.ciphergate.entity.User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    
    /**
     * 根据GitHub ID获取用户
     */
    User getUserByGithubId(String githubId);
    
    /**
     * 创建或更新用户
     */
    User createOrUpdateUser(String githubId, String login, String name, String email, String avatarUrl, String accessToken);
    
    /**
     * 更新用户最后登录时间
     */
    void updateLastLoginTime(Long userId);
    
    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(Long userId, String permissionCode);
    
    /**
     * 检查用户是否有指定角色
     */
    boolean hasRole(Long userId, String roleCode);
    
    /**
     * 获取用户详细信息（包含角色和权限）
     */
    User getUserWithRolesAndPermissions(Long userId);
    
    /**
     * 为新用户分配默认角色
     */
    void assignDefaultRole(Long userId);
    
    /**
     * 查找或创建用户（OAuth2登录）
     */
    User findOrCreateUser(OAuth2User oauth2User);
    
    /**
     * 根据GitHub ID查找用户
     */
    User findByGithubId(String githubId);
    
    /**
     * 获取用户详细信息（包含角色、权限和菜单）
     */
    User getUserWithRolesPermissionsAndMenus(Long userId);
    
    /**
     * 获取所有用户及其角色信息
     */
    List<User> getAllUsersWithRoles();

    /**
     * 根据登录名查找用户
     */
    User findByLogin(String login);

    /**
     * 获取指定角色的所有用户
     */
    List<User> getUsersByRole(String roleCode);
}