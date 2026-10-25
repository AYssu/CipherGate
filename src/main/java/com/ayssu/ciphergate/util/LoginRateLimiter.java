package com.ayssu.ciphergate.util;

import com.ayssu.ciphergate.service.PublicAbuseGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Shared fixed windows survive process restarts; counters and expiry are atomic. */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],900); end; return n", Long.class);
    private static String key(String identity) {
        return "admin:login:fail:" + PublicAbuseGuard.digest(identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT));
    }
    public long getLockRemainingSeconds(String identity) {
        String value = redis.opsForValue().get(key(identity));
        if (value == null || Long.parseLong(value) < 5) return 0;
        Long ttl = redis.getExpire(key(identity), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 900 : Math.max(1, ttl);
    }
    public void recordFailure(String identity) {
        if (redis.execute(FAILURE, List.of(key(identity))) == null) throw new IllegalStateException("登录保护暂不可用");
    }
    public void clearFailures(String identity) { redis.delete(key(identity)); }
}
