package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** 与卡密分开计数；所有尝试均限流，账号只存摘要，不记录密码或原始邮箱。 */
@Service
@RequiredArgsConstructor
public class ThirdPartyUserRateLimitService {
    private final StringRedisTemplate redisTemplate;

    public void checkBeforeLogin(Long appId, String ip, String username) {
        long bucket = Instant.now().getEpochSecond() / 60;
        String source = digest(ip == null ? "unknown-ip" : ip.trim());
        String prefix = "cg:tp:user:rate:" + appId + ":" + source + ":";
        check(prefix + bucket, 120);
        String account = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        check(prefix + digest(account) + ":" + bucket, 20);
    }

    private void check(String key, int limit) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("User login rate limiter unavailable");
        }
        if (count == 1L) {
            // 两分钟覆盖当前分钟桶的剩余有效期；不影响下一分钟的新桶。
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }
        if (count > limit) {
            throw new ThirdPartyUserLoginException(429, "请求过于频繁，请稍后重试");
        }
    }

    private static String digest(String value) {
        return ThirdPartySignatureVerifier.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }
}
