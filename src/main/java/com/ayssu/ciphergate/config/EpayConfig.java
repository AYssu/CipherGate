package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EpayConfig {

    private final SystemConfigService systemConfigService;

    public String getEpayUrl() {
        return systemConfigService.getConfigValue("payment.epay.url", "");
    }

    public String getEpayPid() {
        return systemConfigService.getConfigValue("payment.epay.pid", "");
    }

    public String getEpayKey() {
        return systemConfigService.getConfigValue("payment.epay.key", "");
    }

    public String getEpayNotifyUrl() {
        return systemConfigService.getConfigValue("payment.epay.notify.url", "");
    }

    public String getEpayReturnUrl() {
        return systemConfigService.getConfigValue("payment.epay.return.url", "");
    }

    public String getSuccessRedirectUrl() {
        return systemConfigService.getConfigValue("payment.success.redirect.url", "/user/balance");
    }
}
