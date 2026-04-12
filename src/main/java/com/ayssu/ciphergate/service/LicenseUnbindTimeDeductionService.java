package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 应用级「解绑/换绑扣时」：仅在三方卡密换绑等场景调用；管理员后台解绑不经过此逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseUnbindTimeDeductionService {

    private static final String UNBIND_DEDUCT_NONE = "NONE";
    private static final String UNBIND_DEDUCT_PERCENT = "PERCENT";
    private static final String UNBIND_DEDUCT_HOURS = "HOURS";

    private final ApplicationMapper applicationMapper;

    /**
     * 按应用配置缩短卡密到期时间（存在剩余时长且配置非 NONE 时生效）。
     */
    public void applyIfConfigured(LicenseKey key, LocalDateTime now) {
        Application app = applicationMapper.selectById(key.getAppId());
        if (app == null) {
            return;
        }
        String mode = app.getUnbindTimeDeductMode();
        if (!StringUtils.hasText(mode) || UNBIND_DEDUCT_NONE.equalsIgnoreCase(mode.trim())) {
            return;
        }
        LocalDateTime exp = key.getExpiresAt();
        if (exp == null) {
            return;
        }
        Duration remaining = Duration.between(now, exp);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        BigDecimal val = app.getUnbindTimeDeductValue();
        if (val == null || val.signum() <= 0) {
            return;
        }
        String m = mode.trim().toUpperCase();
        LocalDateTime newExp;
        if (UNBIND_DEDUCT_PERCENT.equals(m)) {
            BigDecimal pct = val.min(BigDecimal.valueOf(100)).max(BigDecimal.ZERO);
            BigDecimal secBd = BigDecimal.valueOf(remaining.getSeconds()).multiply(pct)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            newExp = exp.minusSeconds(Math.max(0L, secBd.longValue()));
        } else if (UNBIND_DEDUCT_HOURS.equals(m)) {
            BigDecimal secBd = val.multiply(BigDecimal.valueOf(3600)).setScale(0, RoundingMode.HALF_UP);
            newExp = exp.minusSeconds(Math.max(0L, secBd.longValue()));
        } else {
            return;
        }
        if (newExp.isBefore(now)) {
            newExp = now;
        }
        key.setExpiresAt(newExp);
        log.info("卡密换绑扣时: keyId={}, mode={}, value={}, newExpiresAt={}", key.getId(), m, val, newExp);
    }

    /**
     * 扣时后若已到期，将状态置为已过期（3），已禁用（4）不修改。
     */
    public void refreshStatusIfExpired(LicenseKey key, LocalDateTime now) {
        if (key.getExpiresAt() == null || key.getExpiresAt().isAfter(now)) {
            return;
        }
        Integer st = key.getStatus();
        if (st != null && st == 4) {
            return;
        }
        key.setStatus(3);
    }
}
