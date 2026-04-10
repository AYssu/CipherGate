package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final LicenseKeyMapper licenseKeyMapper;
    private final AppVariableMapper appVariableMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public CardLoginResponse login(Long appId, CardLoginRequest req, String clientIp) {
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
        if (key.getStatus() != null && key.getStatus() == 4) {
            throw new RuntimeException("卡密已禁用");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("卡密已过期");
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

        Map<String, Object> variables = getAppVariablesForThirdParty(appId);

        CardLoginResponse resp = new CardLoginResponse();
        resp.setAppId(appId);
        resp.setCardId(key.getId());
        resp.setCardCode(key.getKeyCode());
        resp.setExpiresAt(key.getExpiresAt());
        resp.setBindNumber(key.getUseCount());
        resp.setAvailable(resolveAvailableSeconds(key.getExpiresAt()));
        resp.setVariables(variables);
        LocalDateTime lastUsedAt = key.getLastUsedAt();
        boolean online = lastUsedAt != null
                && ChronoUnit.MINUTES.between(lastUsedAt, LocalDateTime.now()) < 5;
        resp.setOnline(online);
        return resp;
    }

    private Long resolveAvailableSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        long seconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0L, seconds);
    }

    private Map<String, Object> getAppVariablesForThirdParty(Long appId) {
        List<AppVariable> variables = appVariableMapper.selectList(new LambdaQueryWrapper<AppVariable>()
                .eq(AppVariable::getAppId, appId)
                .eq(AppVariable::getEnabled, true)
                .eq(AppVariable::getDeleted, 0));
        Map<String, Object> result = new HashMap<>();
        for (AppVariable v : variables) {
            result.put(v.getVariableName(), convertVariableValue(v.getVariableValue(), v.getVariableType()));
        }
        return result;
    }

    private Object convertVariableValue(String value, String type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return switch (type == null ? "" : type) {
                case "NUMBER" -> Double.parseDouble(value);
                case "BOOLEAN" -> Boolean.parseBoolean(value);
                case "JSON" -> objectMapper.readTree(value);
                case "ARRAY" -> objectMapper.readValue(value, List.class);
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
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
        String unit = key.getDurationUnit();
        LocalDateTime now = LocalDateTime.now();
        if (v == null || v <= 0) {
            return now.plusYears(100);
        }
        if (!StringUtils.hasText(unit)) {
            return now.plusDays(v);
        }
        String u = unit.trim().toUpperCase();
        return switch (u) {
            case "MIN", "MINUTE", "MINUTES" -> now.plusMinutes(v);
            case "H", "HOUR", "HOURS" -> now.plusHours(v);
            case "D", "DAY", "DAYS" -> now.plusDays(v);
            case "M", "MONTH", "MONTHS" -> now.plusMonths(v);
            case "Y", "YEAR", "YEARS" -> now.plusYears(v);
            default -> now.plusDays(v);
        };
    }
}

