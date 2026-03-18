package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    
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
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("github_id", githubId);
        User existingUser = userMapper.selectOne(queryWrapper);
        
        if (existingUser != null) {
            log.info("找到现有用户: {}", existingUser);
            
            // 更新最后登录时间和用户信息
            existingUser.setLastLoginAt(LocalDateTime.now());
            existingUser.setName(getStringAttribute(oauth2User, "name"));
            existingUser.setEmail(getStringAttribute(oauth2User, "email"));
            existingUser.setAvatarUrl(getStringAttribute(oauth2User, "avatar_url"));
            existingUser.setUpdatedAt(LocalDateTime.now());
            
            log.info("更新用户信息: name={}, email={}, avatar_url={}", 
                String.valueOf(existingUser.getName()), 
                String.valueOf(existingUser.getEmail()), 
                String.valueOf(existingUser.getAvatarUrl()));
            
            userMapper.updateById(existingUser);
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
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            newUser.setLastLoginAt(LocalDateTime.now());
            
            log.info("新用户信息: githubId={}, login={}, name={}, email={}, avatar_url={}", 
                String.valueOf(newUser.getGithubId()), 
                String.valueOf(newUser.getLogin()), 
                String.valueOf(newUser.getName()), 
                String.valueOf(newUser.getEmail()), 
                String.valueOf(newUser.getAvatarUrl()));
            
            userMapper.insert(newUser);
            log.info("用户创建成功，ID: {}", String.valueOf(newUser.getId()));
            return newUser;
        }
    }
    
    private String getStringAttribute(OAuth2User oauth2User, String attributeName) {
        Object value = oauth2User.getAttribute(attributeName);
        String result = value != null ? value.toString() : null;
        log.debug("属性 [{}]: {} -> {}", 
            attributeName, 
            String.valueOf(value), 
            String.valueOf(result));
        return result;
    }
    
    public User findByGithubId(String githubId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("github_id", githubId);
        User user = userMapper.selectOne(queryWrapper);
        log.info("根据 GitHub ID [{}] 查找用户: {}", 
            String.valueOf(githubId), 
            String.valueOf(user));
        return user;
    }
}