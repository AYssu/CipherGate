package com.ayssu.ciphergate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ayssu.ciphergate.entity.SystemConfig;
import com.ayssu.ciphergate.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {
    
    private final SystemConfigMapper systemConfigMapper;
    
    // 缓存配置，避免频繁查询数据库
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    
    /**
     * 获取配置值
     */
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }
    
    /**
     * 获取配置值，如果不存在则返回默认值
     */
    public String getConfigValue(String configKey, String defaultValue) {
        try {
            // 先从缓存获取
            String cachedValue = configCache.get(configKey);
            if (cachedValue != null) {
                return cachedValue;
            }
            
            // 从数据库获取
            String value = systemConfigMapper.getConfigValue(configKey);
            if (value != null) {
                configCache.put(configKey, value);
                return value;
            }
            
            log.warn("配置项 [{}] 不存在，使用默认值: {}", configKey, defaultValue);
            return defaultValue;
            
        } catch (Exception e) {
            log.error("获取配置项 [{}] 失败: {}", configKey, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * 设置配置值
     */
    public void setConfigValue(String configKey, String configValue) {
        setConfigValue(configKey, configValue, null, false);
    }
    
    /**
     * 设置配置值（完整版本）
     */
    public void setConfigValue(String configKey, String configValue, String description, boolean isEncrypted) {
        try {
            QueryWrapper<SystemConfig> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("config_key", configKey);
            SystemConfig existingConfig = systemConfigMapper.selectOne(queryWrapper);
            
            if (existingConfig != null) {
                // 更新现有配置
                existingConfig.setConfigValue(configValue);
                if (description != null) {
                    existingConfig.setDescription(description);
                }
                existingConfig.setIsEncrypted(isEncrypted);
                systemConfigMapper.updateById(existingConfig);
            } else {
                // 创建新配置
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(configKey);
                newConfig.setConfigValue(configValue);
                newConfig.setDescription(description);
                newConfig.setIsEncrypted(isEncrypted);
                systemConfigMapper.insert(newConfig);
            }
            
            // 更新缓存
            configCache.put(configKey, configValue);
            log.info("配置项 [{}] 已更新", configKey);
            
        } catch (Exception e) {
            log.error("设置配置项 [{}] 失败: {}", configKey, e.getMessage());
        }
    }
    
    /**
     * 刷新缓存
     */
    public void refreshCache() {
        configCache.clear();
        log.info("配置缓存已刷新");
    }
    
    /**
     * 获取 GitHub OAuth2 Client ID
     */
    public String getGithubClientId() {
        return getConfigValue("github.oauth2.client-id", "default-client-id");
    }
    
    /**
     * 获取 GitHub OAuth2 Client Secret
     */
    public String getGithubClientSecret() {
        return getConfigValue("github.oauth2.client-secret", "default-client-secret");
    }
    /**
     * 获取 GitHub OAuth2 Redirect URI
     */
    public String getGithubRedirectUri() {
        return getConfigValue("github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}");
    }
    
    /**
     * 获取前端地址
     */
    public String getFrontendUrl() {
        return getConfigValue("frontend.url", "http://localhost:5173/dashboard");
    }
    
    /**
     * 检查系统是否已初始化（是否还在使用默认配置）
     */
    public boolean isSystemInitialized() {
        String currentClientId = getGithubClientId();
        String currentClientSecret = getGithubClientSecret();
        return isDefaultConfig("github.oauth2.client-id", currentClientId) &&
                isDefaultConfig("github.oauth2.client-secret", currentClientSecret);
    }
    
    /**
     * 检查配置是否为默认值
     */
    public boolean isDefaultConfig(String configKey, String configValue) {
        // 定义默认值
        Map<String, String> defaultValues = Map.of(
            "github.oauth2.client-id", "default-client-id",
            "github.oauth2.client-secret", "default-client-secret",
            "github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}"
        );
        
        String defaultValue = defaultValues.get(configKey);
        return defaultValue != null && defaultValue.equals(configValue);
    }
    
    /**
     * 初始化系统配置（仅在使用默认配置时可用）
     */
    public boolean initializeSystemConfig(String clientId, String clientSecret, String redirectUri, String frontendUrl) {
        // 检查当前配置是否为默认值
        String currentClientId = getGithubClientId();
        String currentClientSecret = getGithubClientSecret();
        String currentRedirectUri = getGithubRedirectUri();
        
        boolean isDefault = isDefaultConfig("github.oauth2.client-id", currentClientId) &&
                           isDefaultConfig("github.oauth2.client-secret", currentClientSecret);
        
        if (!isDefault) {
            log.warn("系统已初始化，无法重复初始化");
            return false;
        }
        
        // 更新配置
        setConfigValue("github.oauth2.client-id", clientId, "GitHub OAuth2 Client ID", false);
        setConfigValue("github.oauth2.client-secret", clientSecret, "GitHub OAuth2 Client Secret", true);
        setConfigValue("github.oauth2.redirect-uri", redirectUri, "GitHub OAuth2 Redirect URI", false);
        setConfigValue("frontend.url", frontendUrl, "前端地址", false);
        setConfigValue("SYSTEM_INITIALIZED", "true", "系统初始化标记", false);
        
        log.info("系统配置初始化成功");
        return true;
    }
}