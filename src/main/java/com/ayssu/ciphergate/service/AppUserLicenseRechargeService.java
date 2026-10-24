package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserLicenseRechargeRequest;
import com.ayssu.ciphergate.dto.AppUserLicenseRechargeResponse;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppUserLicenseRechargeService {
    private final ApplicationMapper applicationMapper;
    private final LicenseKeyMapper licenseKeyMapper;
    private final AppUserMapper appUserMapper;
    private final AppUserLicenseRechargeMapper rechargeMapper;

    @Transactional(rollbackFor = Exception.class)
    public AppUserLicenseRechargeResponse redeem(AppUserLicenseRechargeRequest request, String ip) {
        if (request == null || request.getAppId() == null || request.getAppId() <= 0
                || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getKeyCode())
                || request.getEmail().length() > 100 || request.getKeyCode().length() > 100) {
            throw new IllegalArgumentException("请填写有效的应用、邮箱和卡密");
        }
        Application app = applicationMapper.selectById(request.getAppId());
        if (app == null || Integer.valueOf(1).equals(app.getDeleted())
                || (app.getStatus() != null && app.getStatus() != 1)) {
            throw new IllegalArgumentException("应用不存在或已停用");
        }
        String code = request.getKeyCode().trim().toUpperCase(Locale.ROOT);
        // Card login takes the same row lock: activation and redemption cannot both win.
        LicenseKey key = licenseKeyMapper.selectOne(new LambdaQueryWrapper<LicenseKey>()
                .eq(LicenseKey::getAppId, app.getId()).eq(LicenseKey::getKeyCode, code)
                .last("LIMIT 1 FOR UPDATE"));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (key == null || !app.getId().equals(key.getAppId()) || Integer.valueOf(1).equals(key.getDeleted()) || key.getRedeemedAt() != null || !Integer.valueOf(1).equals(key.getStatus())
                || key.getFirstUsedAt() != null || key.getLastUsedAt() != null || Boolean.TRUE.equals(key.getIsOnline())
                || (key.getUseCount() != null && key.getUseCount() != 0)
                || key.getBindUserId() != null || StringUtils.hasText(key.getBindDeviceId())
                || StringUtils.hasText(key.getBindIp())) {
            throw new IllegalArgumentException("卡密不存在、已使用或不可兑换");
        }
        if (key.getExpiresAt() != null || key.getUseTimeStart() != null || key.getUseTimeEnd() != null) {
            throw new IllegalArgumentException("固定到期或限定使用时段的卡密不支持兑换");
        }
        if (!Set.of("DAY", "WEEK", "MONTH", "QUARTER", "HALF_YEAR", "YEAR", "CUSTOM")
                .contains(normalize(key.getKeyType()))) {
            throw new IllegalArgumentException("仅支持有效的时长卡，永久卡不支持兑换");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AppUser found = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, app.getId()).eq(AppUser::getEmail, email));
        if (found == null) throw new IllegalArgumentException("该应用下未找到此邮箱对应的用户");
        AppUser user = appUserMapper.selectForUpdate(found.getId());
        if (user == null || !app.getId().equals(user.getAppId()) || !email.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("用户信息已变化，请重新确认充值账号");
        }
        LocalDateTime before = user.getMemberExpiresAt();
        LocalDateTime base = before != null && before.isAfter(now) ? before : now;
        if ("CUSTOM".equals(normalize(key.getKeyType())) && key.getDurationValue() == null) {
            throw new IllegalArgumentException("自定义时长卡必须设置正数时长");
        }
        int amount = key.getDurationValue() == null ? 1 : key.getDurationValue();
        if (amount <= 0) throw new IllegalArgumentException("卡密时长无效");
        String unit = StringUtils.hasText(key.getDurationUnit()) ? normalize(key.getDurationUnit()) : normalize(key.getKeyType());
        if ("CUSTOM".equals(unit)) throw new IllegalArgumentException("自定义时长卡必须设置时长单位");
        LocalDateTime after = addDuration(base, amount, unit);
        if (after.getYear() > 9999) throw new IllegalArgumentException("充值后会员到期时间超出范围");
        String masked = code.length() <= 8 ? code.substring(0, Math.min(2, code.length())) + "****"
                : code.substring(0, 4) + "****" + code.substring(code.length() - 4);
        AppUserLicenseRecharge row = new AppUserLicenseRecharge();
        row.setLicenseKeyId(key.getId()); row.setAppId(app.getId()); row.setAppUserId(user.getId());
        row.setKeyCodeMasked(masked); row.setDurationValue(amount); row.setDurationUnit(unit);
        row.setBeforeExpiresAt(before); row.setAfterExpiresAt(after); row.setRequestIp(ip); row.setCreatedAt(now);
        // Unique license_key_id is a second guard even if an administrator resets a card's state.
        if (rechargeMapper.insert(row) != 1) throw new IllegalStateException("充值记录保存失败");
        if (appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>().eq(AppUser::getId, user.getId())
                .eq(AppUser::getAppId, app.getId()).set(AppUser::getMemberExpiresAt, after)
                .set(AppUser::getUpdatedAt, now)) != 1) throw new IllegalStateException("用户额度更新失败");
        if (licenseKeyMapper.update(null, new LambdaUpdateWrapper<LicenseKey>().eq(LicenseKey::getId, key.getId())
                .eq(LicenseKey::getStatus, 1).isNull(LicenseKey::getRedeemedAt).isNull(LicenseKey::getFirstUsedAt)
                .set(LicenseKey::getRedeemedAt, now).set(LicenseKey::getBindUserId, user.getId())
                .set(LicenseKey::getStatus, 3).set(LicenseKey::getFirstUsedAt, now)
                .set(LicenseKey::getIsOnline, false).set(LicenseKey::getExpiresAt, now)
                .set(LicenseKey::getUseCount, 1).set(LicenseKey::getUpdatedAt, now)) != 1) {
            throw new IllegalStateException("卡密状态已变化，请重新查询");
        }
        int at = email.indexOf('@');
        String maskedEmail = at > 1 ? email.substring(0, Math.min(2, at)) + "***" + email.substring(at)
                : "***" + (at >= 0 ? email.substring(at) : "");
        return new AppUserLicenseRechargeResponse(maskedEmail, masked, before, after,
                Math.max(0, Duration.between(now, after).getSeconds()), true);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime addDuration(LocalDateTime base, int amount, String unit) {
        try {
            return switch (unit) {
                case "MIN", "MINUTE", "MINUTES" -> base.plusMinutes(amount);
                case "H", "HOUR", "HOURS" -> base.plusHours(amount);
                case "D", "DAY", "DAYS" -> base.plusDays(amount);
                case "W", "WEEK", "WEEKS" -> base.plusWeeks(amount);
                case "M", "MONTH", "MONTHS" -> base.plusMonths(amount);
                case "QUARTER" -> base.plusMonths(3L * amount);
                case "HALF_YEAR" -> base.plusMonths(6L * amount);
                case "Y", "YEAR", "YEARS" -> base.plusYears(amount);
                default -> throw new IllegalArgumentException("卡密时长单位无效");
            };
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("卡密时长超出范围");
        }
    }
}
