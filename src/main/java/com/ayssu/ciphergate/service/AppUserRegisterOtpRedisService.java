package com.ayssu.ciphergate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 应用用户「自助注册」专用邮箱验证码：仅 Redis 存证，与业务 Redis Key 隔离。
 */
@Service
@RequiredArgsConstructor
public class AppUserRegisterOtpRedisService {

    private static final String OTP_PREFIX = "cg:appuser:reg:otp:";
    private static final String SEND_CD_PREFIX = "cg:appuser:reg:sendcd:";
    /** 验证码仅用于注册；校验阶段不删除，成功注册后删除，否则依赖 TTL 过期 */
    private static final Duration OTP_TTL = Duration.ofMinutes(3);
    /** 同一应用+邮箱发送间隔 */
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    private static String otpKey(Long appId, String emailNormalized) {
        return OTP_PREFIX + appId + ":" + emailNormalized;
    }

    private static String sendCooldownKey(Long appId, String emailNormalized) {
        return SEND_CD_PREFIX + appId + ":" + emailNormalized;
    }

    public void saveOtp(Long appId, String emailNormalized, String code) {
        redisTemplate.opsForValue().set(otpKey(appId, emailNormalized), code, OTP_TTL);
    }

    /**
     * 仅校验验证码是否与 Redis 中一致（不删除）。错误可重试；成功注册后再 {@link #removeOtp}；过期由 TTL 自然失效。
     */
    public boolean matchesOtp(Long appId, String emailNormalized, String inputCode) {
        if (!StringUtils.hasText(inputCode)) {
            return false;
        }
        String expected = redisTemplate.opsForValue().get(otpKey(appId, emailNormalized));
        return expected != null && expected.equals(inputCode.trim());
    }

    /** 注册成功后作废验证码，避免同一验证码被重复使用 */
    public void removeOtp(Long appId, String emailNormalized) {
        redisTemplate.delete(otpKey(appId, emailNormalized));
    }

    public boolean isInSendCooldown(Long appId, String emailNormalized) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sendCooldownKey(appId, emailNormalized)));
    }

    public void markSendCooldown(Long appId, String emailNormalized) {
        redisTemplate.opsForValue().set(sendCooldownKey(appId, emailNormalized), "1", SEND_COOLDOWN);
    }
}
