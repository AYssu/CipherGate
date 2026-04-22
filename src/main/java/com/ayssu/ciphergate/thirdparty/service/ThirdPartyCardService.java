package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.service.AccessEventService;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.service.LicenseUnbindTimeDeductionService;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThirdPartyCardService {

    /** 业务模式：免费（不校验卡密） */
    private static final int BUSINESS_MODEL_FREE = 2;

    private final ApplicationMapper applicationMapper;
    private final LicenseKeyMapper licenseKeyMapper;
    private final ThirdPartyAppVariableService thirdPartyAppVariableService;
    private final LicenseKeyService licenseKeyService;
    private final LicenseUnbindTimeDeductionService licenseUnbindTimeDeductionService;
    private final AccessEventService accessEventService;

    private String toCardTypeDisplay(String keyType) {
        if (!StringUtils.hasText(keyType)) {
            return "未知卡";
        }
        return switch (keyType.trim().toUpperCase()) {
            case "DAY" -> "天卡";
            case "WEEK" -> "周卡";
            case "MONTH" -> "月卡";
            case "QUARTER" -> "季卡";
            case "HALF_YEAR" -> "半年卡";
            case "YEAR" -> "年卡";
            case "PERMANENT" -> "永久卡";
            case "CUSTOM" -> "自定义卡";
            default -> "未知卡";
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public CardLoginResponse login(Long appId, CardLoginRequest req, String clientIp) {
        Application application = applicationMapper.selectById(appId);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        if (application.getBusinessModel() != null && application.getBusinessModel() == BUSINESS_MODEL_FREE) {
            return loginFreeMode(appId, req, clientIp);
        }

        if (!StringUtils.hasText(req.getCardCode()) || !StringUtils.hasText(req.getDeviceId())) {
            throw new RuntimeException("cardCode + deviceId 必填");
        }

        String cardCode = req.getCardCode().trim().toUpperCase();
        LicenseKey key = licenseKeyMapper.selectOne(new LambdaQueryWrapper<LicenseKey>()
                .eq(LicenseKey::getAppId, appId)
                .eq(LicenseKey::getKeyCode, cardCode)
                .eq(LicenseKey::getDeleted, 0)
                .last("limit 1"));
        if (key == null) {
            throw new RuntimeException("卡密不存在");
        }
        licenseKeyService.syncExpiredStatusIfNeeded(key);
        if (key.getStatus() != null && key.getStatus() == 4) {
            throw new RuntimeException("卡密已禁用");
        }
        if (key.getStatus() != null && key.getStatus() == 3) {
            throw new RuntimeException("卡密已过期");
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("卡密已过期");
        }

        int limit = key.getUseLimit() == null ? 0 : key.getUseLimit();
        int used = key.getUseCount() == null ? 0 : key.getUseCount();
        if (limit > 0 && used >= limit) {
            throw new RuntimeException("卡密已达到最大使用次数");
        }

        // device/ip optional check: if license requires, enforce on login
        if (Boolean.TRUE.equals(key.getDeviceCheckEnabled()) && StringUtils.hasText(key.getBindDeviceId())) {
            if (!key.getBindDeviceId().equals(req.getDeviceId())) {
                throw new RuntimeException("设备不匹配");
            }
        }
        if (Boolean.TRUE.equals(key.getIpCheckEnabled()) && StringUtils.hasText(key.getBindIp()) && StringUtils.hasText(clientIp)) {
            if (!key.getBindIp().equals(clientIp)) {
                throw new RuntimeException("IP不匹配");
            }
        }

        // 首次使用时激活：绑定设备/IP并设置到期时间
        if (key.getFirstUsedAt() == null) {
            key.setFirstUsedAt(LocalDateTime.now());
            key.setBindDeviceId(req.getDeviceId());
            key.setBindIp(clientIp);
            if (key.getExpiresAt() == null) {
                key.setExpiresAt(resolveExpiry(key));
            }
            key.setStatus(2);
        } else {
            // 管理员解绑后：已激活卡密在 bindDeviceId/bindIp 为空时允许再次写入绑定信息
            if (Boolean.TRUE.equals(key.getDeviceCheckEnabled()) && !StringUtils.hasText(key.getBindDeviceId())) {
                key.setBindDeviceId(req.getDeviceId());
            }
            if (Boolean.TRUE.equals(key.getIpCheckEnabled()) && !StringUtils.hasText(key.getBindIp()) && StringUtils.hasText(clientIp)) {
                key.setBindIp(clientIp);
            }
        }

        // 无论是否开启IP校验，都记录IP统计；仅在未开启IP校验时允许更新绑定IP
        trackIpUsage(key, clientIp);
        if (!Boolean.TRUE.equals(key.getIpCheckEnabled())) {
            updateBindIpWhenAllowed(key, clientIp);
        }

        Integer keyUseCount = key.getUseCount() == null ? 0 : key.getUseCount();
        key.setUseCount(keyUseCount + 1);
        key.setLastUsedAt(LocalDateTime.now());
        key.setIsOnline(true);
        key.setUpdatedAt(LocalDateTime.now());
        licenseKeyMapper.updateById(key);
        accessEventService.recordCardLogin(appId, key.getId());

        AppVariableTemplateContext variableCtx = new AppVariableTemplateContext();
        variableCtx.setClientIp(clientIp);
        variableCtx.setDeviceId(req.getDeviceId());
        Map<String, Object> variables = thirdPartyAppVariableService.getEnabledVariablesMap(appId, variableCtx);

        CardLoginResponse resp = new CardLoginResponse();
        resp.setAppId(appId);
        resp.setCardId(key.getId());
        resp.setCardCode(key.getKeyCode());
        resp.setCardType(toCardTypeDisplay(key.getKeyType()));
        resp.setExpiresAt(key.getExpiresAt());
        resp.setBindNumber(key.getUseCount());
        resp.setUnbindCount(key.getUnbindCount());
        resp.setUnbindLimit(key.getUnbindLimit());
        resp.setUseLimit(key.getUseLimit());
        resp.setStatus(key.getStatus());
        resp.setFirstUsedAt(key.getFirstUsedAt());
        resp.setLastUsedAt(key.getLastUsedAt());
        resp.setCoreData(key.getCoreData());
        resp.setAvailable(resolveAvailableSeconds(key.getExpiresAt()));
        resp.setVariables(variables);
        LocalDateTime lastUsedAt = key.getLastUsedAt();
        boolean online = lastUsedAt != null
                && ChronoUnit.MINUTES.between(lastUsedAt, LocalDateTime.now()) < 5;
        resp.setOnline(online);
        return resp;
    }

    /**
     * 免费模式：不读不写卡密，仅要求 deviceId；返回固定剩余秒数 99999 与对齐的到期时间，并记访问流水。
     */
    private CardLoginResponse loginFreeMode(Long appId, CardLoginRequest req, String clientIp) {
        if (req == null || !StringUtils.hasText(req.getDeviceId())) {
            throw new RuntimeException("deviceId 必填");
        }
        accessEventService.recordFreeModeCardLogin(appId);

        long availableSeconds = 99_999L;
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(availableSeconds);
        AppVariableTemplateContext variableCtx = new AppVariableTemplateContext();
        variableCtx.setClientIp(clientIp);
        variableCtx.setDeviceId(req.getDeviceId());
        Map<String, Object> variables = thirdPartyAppVariableService.getEnabledVariablesMap(appId, variableCtx);

        CardLoginResponse resp = new CardLoginResponse();
        resp.setAppId(appId);
        resp.setCardId(0L);
        resp.setCardCode("");
        resp.setCardType("免费版");
        resp.setExpiresAt(expiresAt);
        resp.setBindNumber(0);
        resp.setUnbindCount(0);
        resp.setUnbindLimit(0);
        resp.setUseLimit(0);
        resp.setStatus(2);
        resp.setCoreData("");
        resp.setAvailable(availableSeconds);
        resp.setVariables(variables);
        resp.setOnline(false);
        return resp;
    }

    /**
     * 三方卡密换绑设备：新设备与当前绑定一致则失败；否则更新绑定。
     * 若此前已有非空设备绑定，按应用「解绑扣时」配置扣减到期时间（管理员后台解绑不扣时）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CardRebindResponse rebindDevice(Long appId, CardRebindRequest req) {
        Application application = applicationMapper.selectById(appId);
        if (application != null && application.getBusinessModel() != null
                && application.getBusinessModel() == BUSINESS_MODEL_FREE) {
            throw new RuntimeException("免费模式不支持卡密换绑");
        }
        if (!StringUtils.hasText(req.getCardCode()) || !StringUtils.hasText(req.getDeviceId())) {
            throw new RuntimeException("cardCode 与 deviceId 必填");
        }
        String cardCode = req.getCardCode().trim().toUpperCase();
        String newDeviceId = req.getDeviceId().trim();
        LicenseKey key = licenseKeyMapper.selectOne(new LambdaQueryWrapper<LicenseKey>()
                .eq(LicenseKey::getAppId, appId)
                .eq(LicenseKey::getKeyCode, cardCode)
                .eq(LicenseKey::getDeleted, 0)
                .last("limit 1"));
        if (key == null) {
            throw new RuntimeException("卡密不存在");
        }
        licenseKeyService.syncExpiredStatusIfNeeded(key);
        if (key.getStatus() != null && key.getStatus() == 4) {
            throw new RuntimeException("卡密已禁用");
        }
        if (key.getStatus() != null && key.getStatus() == 3) {
            throw new RuntimeException("卡密已过期");
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("卡密已过期");
        }
        if (key.getFirstUsedAt() == null) {
            throw new RuntimeException("卡密尚未激活，请使用登录接口完成首次绑定");
        }
        String current = StringUtils.hasText(key.getBindDeviceId()) ? key.getBindDeviceId().trim() : "";
        if (current.equals(newDeviceId)) {
            throw new RuntimeException("换绑失败，设备一致");
        }
        boolean hadPriorDevice = StringUtils.hasText(current);
        LocalDateTime now = LocalDateTime.now();
        key.setBindDeviceId(newDeviceId);
        if (hadPriorDevice) {
            licenseUnbindTimeDeductionService.applyIfConfigured(key, now);
            licenseUnbindTimeDeductionService.refreshStatusIfExpired(key, now);
        }
        key.setUpdatedAt(now);
        licenseKeyMapper.updateById(key);

        CardRebindResponse out = new CardRebindResponse();
        out.setAppId(appId);
        out.setCardId(key.getId());
        out.setCardCode(key.getKeyCode());
        out.setDeviceId(newDeviceId);
        out.setExpiresAt(key.getExpiresAt());
        out.setAvailable(resolveAvailableSeconds(key.getExpiresAt()));
        AppVariableTemplateContext variableCtx = new AppVariableTemplateContext();
        variableCtx.setDeviceId(newDeviceId);
        out.setVariables(thirdPartyAppVariableService.getEnabledVariablesMap(appId, variableCtx));
        return out;
    }

    private Long resolveAvailableSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long seconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0L, seconds);
    }

    @SuppressWarnings("unchecked")
    private void trackIpUsage(LicenseKey key, String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        Map<String, Object> metadata = key.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        Object statsObj = metadata.get("ipStats");
        Map<String, Integer> ipStats;
        if (statsObj instanceof Map<?, ?> s) {
            ipStats = new HashMap<>();
            for (Map.Entry<?, ?> e : s.entrySet()) {
                String k = String.valueOf(e.getKey());
                Integer v = 0;
                if (e.getValue() instanceof Number n) {
                    v = n.intValue();
                }
                ipStats.put(k, v);
            }
        } else {
            ipStats = new HashMap<>();
        }

        ipStats.put(clientIp, ipStats.getOrDefault(clientIp, 0) + 1);
        metadata.put("ipStats", ipStats);
        key.setMetadata(metadata);
    }

    @SuppressWarnings("unchecked")
    private void updateBindIpWhenAllowed(LicenseKey key, String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        String oldIp = key.getBindIp();
        if (!StringUtils.hasText(oldIp)) {
            key.setBindIp(clientIp);
            return;
        }
        if (oldIp.equals(clientIp)) {
            return;
        }

        Map<String, Object> metadata = key.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }

        Object historyObj = metadata.get("ipHistory");
        List<Map<String, Object>> ipHistory;
        if (historyObj instanceof List<?> list) {
            ipHistory = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        row.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    ipHistory.add(row);
                }
            }
        } else {
            ipHistory = new ArrayList<>();
        }

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("from", oldIp);
        change.put("to", clientIp);
        change.put("at", LocalDateTime.now().toString());
        ipHistory.add(change);
        metadata.put("ipHistory", ipHistory);
        key.setMetadata(metadata);

        key.setBindIp(clientIp);
    }

    private LocalDateTime resolveExpiry(LicenseKey key) {
        if (key.getExpiresAt() != null) {
            return key.getExpiresAt();
        }
        Integer v = key.getDurationValue();
        int mult = (v == null || v <= 0) ? 1 : v;
        LocalDateTime now = LocalDateTime.now();
        String unit = key.getDurationUnit();
        if (StringUtils.hasText(unit)) {
            String u = unit.trim().toUpperCase();
            return switch (u) {
                case "MIN", "MINUTE", "MINUTES" -> now.plusMinutes(mult);
                case "H", "HOUR", "HOURS" -> now.plusHours(mult);
                case "D", "DAY", "DAYS" -> now.plusDays(mult);
                case "W", "WEEK", "WEEKS" -> now.plusWeeks(mult);
                case "M", "MONTH", "MONTHS" -> now.plusMonths(mult);
                case "QUARTER" -> now.plusMonths(3L * mult);
                case "HALF_YEAR" -> now.plusMonths(6L * mult);
                case "Y", "YEAR", "YEARS" -> now.plusYears(mult);
                default -> now.plusDays(mult);
            };
        }
        // 未写 durationUnit 时按卡密类型推算（与后台创建/批量生成表单一致）
        String kt = key.getKeyType() != null ? key.getKeyType().trim().toUpperCase() : "";
        return switch (kt) {
            case "PERMANENT" -> now.plusYears(100);
            case "DAY" -> now.plusDays(mult);
            case "WEEK" -> now.plusWeeks(mult);
            case "MONTH" -> now.plusMonths(mult);
            case "QUARTER" -> now.plusMonths(3L * mult);
            case "HALF_YEAR" -> now.plusMonths(6L * mult);
            case "YEAR" -> now.plusYears(mult);
            case "CUSTOM" -> {
                if (v == null || v <= 0) {
                    yield now.plusYears(100);
                }
                yield now.plusDays(mult);
            }
            default -> {
                if (v == null || v <= 0) {
                    yield now.plusYears(100);
                }
                yield now.plusDays(mult);
            }
        };
    }
}

