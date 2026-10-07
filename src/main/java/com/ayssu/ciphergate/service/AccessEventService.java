package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.constant.AccessEventTypes;
import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 成功登录流水。统计身份不具备授权能力，不创建虚拟用户或卡密。
 * 设备标识仅存应用隔离的 SHA-256 摘要，不保存原始设备指纹。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessEventService {

    private final AccessEventMapper accessEventMapper;

    public record LoginIdentity(String identityType, String identityId) {}

    public LoginIdentity recordCardLogin(Long appId, Long licenseKeyId, String deviceId, String clientIp) {
        String hash = deviceHash(appId, deviceId);
        insert(AccessEventTypes.CARD_LOGIN, appId, licenseKeyId, hash, clientIp);
        return new LoginIdentity("CARD", "card_" + licenseKeyId);
    }

    public LoginIdentity recordFreeModeCardLogin(Long appId, String deviceId, String clientIp) {
        String hash = deviceHash(appId, deviceId);
        insert(AccessEventTypes.CARD_LOGIN_FREE, appId, 0L, hash, clientIp);
        return new LoginIdentity("VISITOR", hash == null ? null : "visitor_" + hash);
    }

    // 保留旧调用方式；历史免费流水没有设备信息，不参与独立访客统计。
    public void recordCardLogin(Long appId, Long licenseKeyId) {
        insert(AccessEventTypes.CARD_LOGIN, appId, licenseKeyId, null, null);
    }

    public void recordFreeModeCardLogin(Long appId) {
        insert(AccessEventTypes.CARD_LOGIN_FREE, appId, 0L, null, null);
    }

    public void recordAppUserWsLogin(Long appId, Long appUserId) {
        insert(AccessEventTypes.APP_USER_WS_LOGIN, appId, appUserId, null, null);
    }

    static String deviceHash(Long appId, String deviceId) {
        if (appId == null || !StringUtils.hasText(deviceId)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("cg:login-device:v1:" + appId + ":" + deviceId.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void insert(String type, Long appId, Long refId, String deviceHash, String clientIp) {
        if (appId == null || refId == null || type == null) {
            return;
        }
        try {
            AccessEvent row = new AccessEvent();
            row.setEventType(type);
            row.setAppId(appId);
            row.setRefId(refId);
            row.setDeviceHash(deviceHash);
            // 防止非标准地址超过数据库字段长度，统计失败不阻断登录。
            String ip = clientIp == null ? null : clientIp.trim();
            row.setClientIp(StringUtils.hasText(ip) && ip.length() <= 64 ? ip : null);
            row.setCreatedAt(LocalDateTime.now());
            accessEventMapper.insert(row);
        } catch (Exception e) {
            log.warn("access_event 写入失败 type={} appId={} refId={}: {}", type, appId, refId, e.getMessage());
        }
    }
}
