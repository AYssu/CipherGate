package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/** 只补缺失的业务默认配置；表结构与权限/菜单种子由 Flyway 管理。 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOnDatabaseInitialization
public class DefaultSystemConfigInitializer implements CommandLineRunner {
    private final SystemConfigService systemConfigService;

    @Override
    public void run(String... args) {
        initializeDefaultConfigs();
    }

    private void initializeDefaultConfigs() {
        try {
            // 检查并初始化 GitHub OAuth2 配置
            String clientId = systemConfigService.getConfigValue("github.oauth2.client-id");
            if (clientId == null) {
                systemConfigService.setConfigValue("github.oauth2.client-id", "default-client-id", "GitHub OAuth2 Client ID", false);
                log.info("初始化默认 GitHub Client ID");
            }

            String clientSecret = systemConfigService.getConfigValue("github.oauth2.client-secret");
            if (clientSecret == null) {
                systemConfigService.setConfigValue("github.oauth2.client-secret", "default-client-secret", "GitHub OAuth2 Client Secret", true);
                log.info("初始化默认 GitHub Client Secret");
            }

            String redirectUri = systemConfigService.getConfigValue("github.oauth2.redirect-uri");
            if (redirectUri == null) {
                systemConfigService.setConfigValue("github.oauth2.redirect-uri", "{baseUrl}/login/oauth2/code/{registrationId}", "GitHub OAuth2 Redirect URI", false);
                log.info("初始化默认 GitHub Redirect URI");
            }

            // 初始化支付配置（如果不存在）
            initPaymentConfig("payment.epay.url", "", "易支付接口地址", false);
            initPaymentConfig("payment.epay.pid", "", "易支付商户ID", false);
            initPaymentConfig("payment.epay.key", "", "易支付密钥", true);
            initPaymentConfig("payment.epay.notify.url", "", "易支付异步回调地址", false);
            initPaymentConfig("payment.epay.return.url", "", "易支付同步跳转地址", false);
            initPaymentConfig("payment.success.redirect.url", "", "支付成功跳转地址", false);
            // 门户支付回调和跳转地址（固定路径，终端用户购买使用）
            initPaymentConfig("payment.portal.notify.url", "", "门户支付异步回调地址", false);
            initPaymentConfig("payment.portal.return.url", "", "门户支付同步跳转地址", false);
            initPaymentConfig("payment.portal.success.url", "", "门户支付成功跳转地址", false);

        } catch (Exception e) {
            throw new IllegalStateException("初始化默认配置失败", e);
        }
    }

    private void initPaymentConfig(String key, String defaultValue, String description, boolean encrypted) {
        String value = systemConfigService.getConfigValue(key);
        if (value == null) {
            systemConfigService.setConfigValue(key, defaultValue, description, encrypted);
        }
    }
}
