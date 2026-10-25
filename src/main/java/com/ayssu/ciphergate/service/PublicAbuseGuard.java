package com.ayssu.ciphergate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Shared, atomic Redis windows. Redis errors fail closed rather than disabling protection. */
@Service
@RequiredArgsConstructor
public class PublicAbuseGuard {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> WINDOW = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    public void check(String scope, String identity, long limit, Duration window) {
        Long n = redis.execute(WINDOW, List.of("abuse:" + scope + ":" + digest(identity)), Long.toString(window.toMillis()));
        if (n == null || n > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
