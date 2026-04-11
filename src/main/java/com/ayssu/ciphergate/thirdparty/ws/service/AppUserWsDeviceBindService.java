package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * WS 账号登录时，按设备标识维护 {@link AppUserBinding}（同一 app + user + deviceId 唯一）。
 * 绑定类型 {@value #BIND_TYPE_ACCOUNT} 表示账号口令登录产生的设备记录。
 */
@Service
@RequiredArgsConstructor
public class AppUserWsDeviceBindService {

    public static final String BIND_TYPE_ACCOUNT = "ACCOUNT";

    private static final int MAX_DEVICE_ID = 255;
    private static final int MAX_DEVICE_NAME = 100;
    private static final int MAX_DEVICE_OS = 50;

    private final AppUserBindingMapper appUserBindingMapper;

    @Transactional(rollbackFor = Exception.class)
    public void bindOrTouchOnWsLogin(Long appId, Long userId, String deviceId, String deviceName, String deviceOs, String clientIp) {
        if (appId == null || userId == null) {
            throw new IllegalArgumentException("appId/userId required");
        }
        AppUserBinding row = appUserBindingMapper.selectByAppUserDeviceRaw(appId, userId, deviceId);
        LocalDateTime now = LocalDateTime.now();
        String ip = StringUtils.hasText(clientIp) ? clientIp.trim() : null;
        String dName = trimTo(deviceName, MAX_DEVICE_NAME);
        String dOs = trimTo(deviceOs, MAX_DEVICE_OS);

        if (row != null) {
            if (Boolean.TRUE.equals(row.getIsBanned()) || (row.getStatus() != null && row.getStatus() == 3)) {
                throw new IllegalStateException("DEVICE_BANNED");
            }
            String bt = row.getBindType();
            if (StringUtils.hasText(bt) && !BIND_TYPE_ACCOUNT.equalsIgnoreCase(bt.trim())) {
                throw new IllegalStateException("DEVICE_CONFLICT"); // 如已为 LICENSE 等，不允许 WS 覆盖
            }
            appUserBindingMapper.updateWsAccountDeviceTouchById(
                    row.getId(),
                    BIND_TYPE_ACCOUNT,
                    dName,
                    dOs,
                    ip,
                    now
            );
            return;
        }

        AppUserBinding ins = new AppUserBinding();
        ins.setAppId(appId);
        ins.setUserId(userId);
        ins.setBindType(BIND_TYPE_ACCOUNT);
        ins.setLicenseKeyId(null);
        ins.setDeviceId(trimTo(deviceId, MAX_DEVICE_ID));
        ins.setDeviceName(dName);
        ins.setDeviceOs(dOs);
        ins.setDeviceIp(ip);
        ins.setFirstBindAt(now);
        ins.setLastActiveAt(now);
        ins.setUseCount(1);
        ins.setUnbindCount(0);
        ins.setIsTrial(false);
        ins.setAllowUnbind(true);
        ins.setIsBanned(false);
        ins.setStatus(1);
        ins.setDeleted(0);
        ins.setCreatedAt(now);
        ins.setUpdatedAt(now);
        appUserBindingMapper.insert(ins);
    }

    private static String trimTo(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
