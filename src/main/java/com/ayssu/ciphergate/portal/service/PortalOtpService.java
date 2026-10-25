package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.portal.entity.PortalVerifyCode;
import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import com.ayssu.ciphergate.service.mail.SystemSmtpMailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/** DB row locks make OTP consumption and the caller's credential update one transaction. */
@Service
@RequiredArgsConstructor
public class PortalOtpService {
    private final PortalVerifyCodeMapper codes;
    private final StringRedisTemplate redis;
    private final PublicAbuseGuard guard;
    private final SystemSmtpMailService mail;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String emailChangePurpose(AppUser user) { return "EMAIL_CHANGE:" + user.getId() + ":" + user.getAppId(); }

    public void claimSend(String email, String purpose) {
        if (email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("请输入有效邮箱");
        guard.check("portal-mail-email", email, 6, Duration.ofHours(1));
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("portal:otp:send:" + PublicAbuseGuard.digest(purpose + ":" + email), "1", Duration.ofSeconds(60))))
            throw new IllegalArgumentException("发送过于频繁，请60秒后重试");
    }

    @Transactional
    public void send(String email, String purpose) {
        if (!mail.isMailEnabledAndConfigured()) throw new IllegalStateException("邮件服务暂不可用");
        // Issuing a new challenge invalidates all earlier codes for this identity/purpose.
        codes.update(null, new LambdaUpdateWrapper<PortalVerifyCode>().eq(PortalVerifyCode::getEmail, email)
            .eq(PortalVerifyCode::getPurpose, purpose).eq(PortalVerifyCode::getUsed, false).set(PortalVerifyCode::getUsed, true));
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PortalVerifyCode row = new PortalVerifyCode();
        row.setEmail(email); row.setPurpose(purpose); row.setCode(code); row.setUsed(false);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(5)); codes.insert(row);
        try { mail.sendPlainText(email, "邮箱验证码", "您的验证码是: " + code + "\n有效期5分钟，请勿泄露给他人。"); }
        catch (Exception ex) { throw new IllegalStateException("邮件发送失败，请稍后重试"); }
    }

    @Transactional
    public void verify(String email, String purpose, String supplied, boolean consume) {
        PortalVerifyCode row = codes.selectOne(new LambdaQueryWrapper<PortalVerifyCode>()
            .eq(PortalVerifyCode::getEmail, email).eq(PortalVerifyCode::getPurpose, purpose)
            .eq(PortalVerifyCode::getUsed, false).orderByDesc(PortalVerifyCode::getId).last("LIMIT 1 FOR UPDATE"));
        if (row == null || row.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("验证码无效或已过期");
        // Attempts live outside SQL rollback so throwing an invalid-code exception cannot undo the limit.
        guard.check("portal-otp-attempt", row.getId().toString(), 8, Duration.ofMinutes(10));
        if (supplied == null || !java.security.MessageDigest.isEqual(row.getCode().getBytes(java.nio.charset.StandardCharsets.UTF_8), supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("验证码无效或已过期");
        if (consume && codes.update(null, new LambdaUpdateWrapper<PortalVerifyCode>().eq(PortalVerifyCode::getId, row.getId())
                .eq(PortalVerifyCode::getUsed, false).set(PortalVerifyCode::getUsed, true)) != 1)
            throw new IllegalArgumentException("验证码无效或已过期");
    }
}
