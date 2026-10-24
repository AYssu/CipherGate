package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.service.AccessEventService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 账号登录成功后写入（WS 与 HTTP 共用计数逻辑）：登录次数、最后登录时间/IP、最后登录设备标识。
 */
@Service
@RequiredArgsConstructor
public class AppUserWsLoginRecorder {

    private final AppUserMapper appUserMapper;
    private final AccessEventService accessEventService;

    @Transactional(rollbackFor = Exception.class)
    public void recordSuccessfulLogin(Long appId, Long appUserId, String clientIp, String deviceId) {
        updateLoginDetails(appId, appUserId, clientIp, deviceId);
        accessEventService.recordAppUserWsLogin(appId, appUserId);
    }

    /** HTTP 流水由调用方在变量渲染成功后记录，不冒充 WS AUTH。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordSuccessfulHttpLogin(Long appId, Long appUserId, String clientIp, String deviceId) {
        updateLoginDetails(appId, appUserId, clientIp, deviceId);
    }

    private void updateLoginDetails(Long appId, Long appUserId, String clientIp, String deviceId) {
        if (appUserId == null) {
            return;
        }
        String ip = StringUtils.hasText(clientIp) ? clientIp.trim() : "";
        String dev = StringUtils.hasText(deviceId) ? deviceId.trim() : null;
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<AppUser> uw = new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, appUserId)
                .eq(AppUser::getAppId, appId)
                .setSql("login_count = COALESCE(login_count, 0) + 1")
                .set(AppUser::getLastLoginAt, now)
                .set(AppUser::getLastLoginIp, ip.isEmpty() ? null : ip)
                .set(AppUser::getUpdatedAt, now);
        if (dev != null) {
            uw.set(AppUser::getLastDeviceId, dev);
        }
        appUserMapper.update(null, uw);
    }
}
