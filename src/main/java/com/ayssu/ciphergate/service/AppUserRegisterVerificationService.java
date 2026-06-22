package com.ayssu.ciphergate.service;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 应用终端用户自助注册：发送邮箱验证码（Redis 3 分钟）+ 校验后落库。
 * 验证码 Redis Key 前缀独立，仅用于本注册流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserRegisterVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int IP_SEND_MAX_PER_HOUR = 80;
    private static final String IP_RL_PREFIX = "cg:appuser:reg:iprl:";

    private final ApplicationMapper applicationMapper;
    private final AppUserMapper appUserMapper;
    private final AppUserRegisterOtpRedisService otpRedisService;
    private final SystemSmtpMailService systemSmtpMailService;
    private final StringRedisTemplate redisTemplate;
    private final UserMembershipService userMembershipService;

    public void sendRegisterEmailVerificationCode(Long appId, String emailRaw, String clientIp) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("应用无效");
        }
        if (!StringUtils.hasText(emailRaw)) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String email = normalizeEmail(emailRaw);
        if (email.length() > 100) {
            throw new IllegalArgumentException("邮箱过长");
        }

        if (!systemSmtpMailService.isMailEnabledAndConfigured()) {
            throw new IllegalStateException("邮件服务未启用或未完整配置，请联系管理员");
        }

        checkIpSendLimit(clientIp);

        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new IllegalArgumentException("应用不存在");
        }
        if (app.getStatus() != null && app.getStatus() != 1) {
            throw new IllegalStateException("应用暂不可注册");
        }

        if (otpRedisService.isInSendCooldown(appId, email)) {
            throw new IllegalStateException("发送过于频繁，请 60 秒后再试");
        }

        LambdaQueryWrapper<AppUser> dup = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0);
        if (appUserMapper.selectCount(dup) > 0) {
            throw new IllegalStateException("该邮箱已注册本应用");
        }

        String code = String.format("%06d", 100000 + RANDOM.nextInt(900000));
        String appLabel = StringUtils.hasText(app.getAppName()) ? app.getAppName() : ("应用 " + appId);
        // 主题避免长破折号等特殊符号，降低部分 SMTP（如 QQ）返回 502 的概率
        String subject = "[CipherGate] " + appLabel + " - 注册验证码";
        String body = "您正在注册应用「" + appLabel + "」的终端账号。\n\n"
                + "验证码：" + code + "\n"
                + "有效期 3 分钟，请勿泄露给他人。\n\n"
                + "如非本人操作，请忽略本邮件。\n";

        systemSmtpMailService.sendPlainText(email, subject, body);
        otpRedisService.saveOtp(appId, email, code);
        otpRedisService.markSendCooldown(appId, email);
        log.info("应用用户注册验证码已发送: appId={}, email={}", appId, email);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeSelfRegister(Long appId, String usernameRaw, String emailRaw, String emailCode, String password) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("应用无效");
        }
        String username = normalizeAndValidateUsername(usernameRaw);
        String email = normalizeEmail(emailRaw);
        if (email.length() > 100) {
            throw new IllegalArgumentException("邮箱过长");
        }

        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new IllegalArgumentException("应用不存在");
        }
        if (app.getStatus() != null && app.getStatus() != 1) {
            throw new IllegalStateException("应用暂不可注册");
        }

        if (!userMembershipService.checkUserRegisterQuota(app.getOwnerId(), 1)) {
            throw new IllegalStateException("终端用户注册额度不足，请联系应用管理员升级会员");
        }

        if (!otpRedisService.matchesOtp(appId, email, emailCode)) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        LambdaQueryWrapper<AppUser> dupEmail = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0);
        if (appUserMapper.selectCount(dupEmail) > 0) {
            throw new IllegalStateException("该邮箱已注册本应用");
        }

        LambdaQueryWrapper<AppUser> dupUsername = new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getUsername, username)
                .eq(AppUser::getDeleted, 0);
        if (appUserMapper.selectCount(dupUsername) > 0) {
            throw new IllegalStateException("该用户名已被占用");
        }

        AppUser row = new AppUser();
        row.setAppId(appId);
        row.setUsername(username);
        row.setEmail(email);
        row.setPassword(BCrypt.hashpw(password));
        row.setNickname(username);
        row.setLoginCount(0);
        row.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        appUserMapper.insert(row);
        otpRedisService.removeOtp(appId, email);
        userMembershipService.consumeUserRegisterQuota(app.getOwnerId(), 1);
        log.info("应用用户自助注册成功: appId={}, userId={}, username={}", appId, row.getId(), username);
    }

    private void checkIpSendLimit(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        String key = IP_RL_PREFIX + clientIp;
        Long n = redisTemplate.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (n != null && n > IP_SEND_MAX_PER_HOUR) {
            throw new IllegalStateException("请求过于频繁，请稍后再试");
        }
    }

    private static String normalizeEmail(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** 与库表 username 一致：trim，2～50，仅字母数字下划线中划线 */
    private static String normalizeAndValidateUsername(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String u = raw.trim();
        if (u.length() < 2 || u.length() > 50) {
            throw new IllegalArgumentException("用户名须为 2～50 个字符");
        }
        if (!USERNAME_PATTERN.matcher(u).matches()) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线或中划线");
        }
        return u;
    }
}
