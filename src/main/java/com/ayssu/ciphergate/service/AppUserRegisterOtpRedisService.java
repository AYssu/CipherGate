package com.ayssu.ciphergate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;

/** Registration-only OTP, five total attempts, one successful atomic consumption. */
@Service
@RequiredArgsConstructor
public class AppUserRegisterOtpRedisService {
    private final StringRedisTemplate redisTemplate;
    private static final Duration OTP_TTL = Duration.ofMinutes(3);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
        "local v=redis.call('GET',KEYS[1]); if not v then return 0 end; " +
        "local n=redis.call('INCR',KEYS[2]); if n==1 then redis.call('PEXPIRE',KEYS[2],180000) end; " +
        "if n>5 then redis.call('DEL',KEYS[1]); return 0 end; " +
        "if v==ARGV[1] then redis.call('DEL',KEYS[1],KEYS[2]); return 1 end; " +
        "if n==5 then redis.call('DEL',KEYS[1]) end; return 0", Long.class);
    private static String otpKey(Long appId, String email) { return "cg:appuser:reg:otp:" + appId + ":" + PublicAbuseGuard.digest(email); }
    private static String cooldownKey(Long appId, String email) { return "cg:appuser:reg:sendcd:" + appId + ":" + PublicAbuseGuard.digest(email); }
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>(
        "redis.call('DEL',KEYS[2]); redis.call('SET',KEYS[1],ARGV[1],'PX',ARGV[2]); return 1", Long.class);
    public void saveOtp(Long appId, String email, String code) {
        if (!Long.valueOf(1).equals(redisTemplate.execute(SAVE, List.of(otpKey(appId, email), otpKey(appId, email) + ":attempts"), code, Long.toString(OTP_TTL.toMillis()))))
            throw new IllegalStateException("验证码服务暂不可用");
    }
    public boolean matchesOtp(Long appId, String email, String code) {
        if (code == null || code.isBlank()) return false;
        return Long.valueOf(1).equals(redisTemplate.execute(CONSUME,
            List.of(otpKey(appId, email), otpKey(appId, email) + ":attempts"), code.trim()));
    }
    public void removeOtp(Long appId, String email) { redisTemplate.delete(otpKey(appId, email)); }
    public boolean reserveSend(Long appId, String email) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(cooldownKey(appId, email), "1", SEND_COOLDOWN));
    }
}
