package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.PublicLicenseQueryRequest;
import com.ayssu.ciphergate.dto.PublicLicenseQueryResponse;
import com.ayssu.ciphergate.dto.PublicLicenseUnbindRequest;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.service.PublicLicenseSelfService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicLicenseSelfServiceImpl implements PublicLicenseSelfService {

    private final LicenseKeyMapper licenseKeyMapper;
    private final ApplicationMapper applicationMapper;

    @Override
    public PublicLicenseQueryResponse queryRemaining(PublicLicenseQueryRequest req) {
        LicenseKey key = findKey(req.getAppId(), req.getKeyCode());
        PublicLicenseQueryResponse resp = new PublicLicenseQueryResponse();
        resp.setKeyCodeMasked(maskKeyCode(key.getKeyCode()));
        resp.setStatus(key.getStatus());
        resp.setExpiresAt(key.getExpiresAt());
        resp.setBoundDevice(StringUtils.hasText(key.getBindDeviceId()));
        resp.setBoundIp(StringUtils.hasText(key.getBindIp()));
        resp.setRemainingSeconds(calcRemainingSeconds(key.getExpiresAt()));
        int used = key.getUnbindCount() == null ? 0 : key.getUnbindCount();
        int limit = key.getUnbindLimit() == null ? 0 : key.getUnbindLimit();
        resp.setUnbindCount(used);
        resp.setUnbindLimit(limit);
        resp.setUnbindRemaining(limit <= 0 ? -1 : Math.max(0, limit - used));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(PublicLicenseUnbindRequest req) {
        boolean unbindDevice = req.getUnbindDevice() == null || Boolean.TRUE.equals(req.getUnbindDevice());
        boolean unbindIp = req.getUnbindIp() == null || Boolean.TRUE.equals(req.getUnbindIp());
        if (!unbindDevice && !unbindIp) {
            throw new IllegalArgumentException("请至少选择一种解绑类型");
        }
        LicenseKey key = findKey(req.getAppId(), req.getKeyCode());
        Application app = applicationMapper.selectById(req.getAppId());
        ensureUnbindQuota(key);

        boolean hasDev = StringUtils.hasText(key.getBindDeviceId());
        boolean hasIp = StringUtils.hasText(key.getBindIp());
        boolean changed = (unbindDevice && hasDev) || (unbindIp && hasIp);
        if (!changed) {
            throw new IllegalStateException(unbindDevice && unbindIp ? "当前未绑定设备/IP" : (unbindDevice ? "当前未绑定设备" : "当前未绑定IP"));
        }

        LocalDateTime now = LocalDateTime.now();
        ensureRebindCooldown(app, key, now);
        int used = key.getUnbindCount() == null ? 0 : key.getUnbindCount();
        int nextUsed = used + 1;
        Map<String, Object> metadata = markLastRebindAt(key, now);

        // 先通过实体更新 JSON 字段，确保 metadata 使用 JacksonTypeHandler 正确序列化
        key.setUpdatedAt(now);
        key.setUnbindCount(nextUsed);
        key.setMetadata(metadata);
        licenseKeyMapper.updateById(key);

        // 再显式清空绑定字段（updateById 默认会忽略 null 字段）
        LambdaUpdateWrapper<LicenseKey> uw = new LambdaUpdateWrapper<LicenseKey>()
                .eq(LicenseKey::getId, key.getId())
                .set(LicenseKey::getUpdatedAt, now);
        if (unbindDevice && hasDev) {
            uw.set(LicenseKey::getBindDeviceId, null);
        }
        if (unbindIp && hasIp) {
            uw.set(LicenseKey::getBindIp, null);
        }
        if ((unbindDevice && hasDev) || (unbindIp && hasIp)) {
            licenseKeyMapper.update(null, uw);
        }
        log.info("公开卡密解绑: appId={}, keyId={}, keyCode={}, unbindDevice={}, unbindIp={}, unbindCount->{}",
                req.getAppId(), key.getId(), key.getKeyCode(), unbindDevice, unbindIp, nextUsed);
    }

    private LicenseKey findKey(Long appId, String keyCodeRaw) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("应用ID无效");
        }
        String keyCode = keyCodeRaw == null ? "" : keyCodeRaw.trim().toUpperCase();
        if (!StringUtils.hasText(keyCode)) {
            throw new IllegalArgumentException("卡密不能为空");
        }
        LicenseKey key = licenseKeyMapper.selectOne(new LambdaQueryWrapper<LicenseKey>()
                .eq(LicenseKey::getAppId, appId)
                .eq(LicenseKey::getKeyCode, keyCode));
        if (key == null) {
            throw new IllegalArgumentException("卡密不存在");
        }
        return key;
    }

    private static long calcRemainingSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return -1L;
        }
        long sec = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0L, sec);
    }

    private static String maskKeyCode(String keyCode) {
        if (!StringUtils.hasText(keyCode)) {
            return "";
        }
        String k = keyCode.trim();
        if (k.length() <= 8) {
            return k.substring(0, Math.min(2, k.length())) + "****";
        }
        return k.substring(0, 4) + "****" + k.substring(k.length() - 4);
    }

    private static void ensureUnbindQuota(LicenseKey key) {
        int limit = key.getUnbindLimit() == null ? 0 : key.getUnbindLimit();
        if (limit <= 0) {
            return;
        }
        int used = key.getUnbindCount() == null ? 0 : key.getUnbindCount();
        if (used >= limit) {
            throw new IllegalStateException("解绑次数已达上限（" + limit + " 次），无法继续解绑");
        }
    }

    private static void ensureRebindCooldown(Application app, LicenseKey key, LocalDateTime now) {
        int cooldownHours = app == null || app.getUnbindCooldownHours() == null ? 0 : app.getUnbindCooldownHours();
        if (cooldownHours <= 0) {
            return;
        }
        Map<String, Object> metadata = key.getMetadata();
        if (metadata == null) {
            return;
        }
        Object raw = metadata.get("lastRebindAt");
        if (!(raw instanceof String s) || !StringUtils.hasText(s)) {
            return;
        }
        try {
            LocalDateTime last = LocalDateTime.parse(s.trim());
            LocalDateTime nextAt = last.plusHours(cooldownHours);
            if (now.isBefore(nextAt)) {
                long remainMin = Math.max(1L, Duration.between(now, nextAt).toMinutes());
                throw new IllegalStateException("换绑冷却中，请 " + remainMin + " 分钟后重试");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignored) {
            // 脏值不阻断
        }
    }

    private static Map<String, Object> markLastRebindAt(LicenseKey key, LocalDateTime now) {
        Map<String, Object> metadata = key.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        } else {
            metadata = new HashMap<>(metadata);
        }
        metadata.put("lastRebindAt", now.truncatedTo(ChronoUnit.SECONDS).toString());
        return metadata;
    }
}
