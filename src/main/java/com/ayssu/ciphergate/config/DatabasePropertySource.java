package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DatabasePropertySource implements ApplicationListener<ApplicationReadyEvent> {
    
    private final SystemConfigService systemConfigService;
    
    public DatabasePropertySource(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("验证数据库配置加载状态...");
            
            // 验证配置是否正确加载
            String githubClientId = systemConfigService.getGithubClientId();
            String githubClientSecret = systemConfigService.getGithubClientSecret();
            
            log.info("数据库配置验证完成:");
            log.info("GitHub Client ID: {}", githubClientId);
            log.info("GitHub Client Secret: {}***", githubClientSecret.substring(0, Math.min(8, githubClientSecret.length())));
            
        } catch (Exception e) {
            log.error("数据库配置验证失败: {}", e.getMessage(), e);
        }
    }
}