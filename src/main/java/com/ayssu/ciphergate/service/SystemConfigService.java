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
}