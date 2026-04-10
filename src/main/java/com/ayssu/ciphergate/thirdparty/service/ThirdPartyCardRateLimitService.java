package com.ayssu.ciphergate.thirdparty.service;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.util.Locale;

/**
 * 三方卡密登录限流器。
 *
 * 设计原则：
 * 1) 不对 cardCode 做全局封禁，避免被恶意“打挂别人的卡”。
 * 2) 主要按 appId + ip 做限流和短期封禁。
 * 3) 增加 appId + ip + cardCode 维度的细粒度频控，限制同一来源对同一卡密暴力尝试。
 */
@Service
public class ThirdPartyCardRateLimitService {

    // appId + ip：一分钟最多 N 次
    private static final int APP_IP_MAX_PER_MINUTE = 120;
    private static final long APP_IP_WINDOW_MS = 60_000L;

    // appId + ip + cardCode：一分钟最多 N 次
    private static final int APP_IP_CARD_MAX_PER_MINUTE = 20;
    private static final long APP_IP_CARD_WINDOW_MS = 60_000L;

    // 连续失败：仅按 appId + ip 统计，不按 cardCode 统计
    private static final int FAIL_STREAK_THRESHOLD = 12;
    private static final long FAIL_STREAK_WINDOW_MS = 5 * 60_000L;
    private static final long BAN_MS = 3 * 60_000L;

    private final StringRedisTemplate redisTemplate;

    public ThirdPartyCardRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkBeforeLogin(Long appId, String ip, String cardCode) {
        try {
            long now = Instant.now().toEpochMilli();
            String appIpKey = buildAppIpKey(appId, ip);

            String banKey = "cg:tp:card:ban:" + appIpKey;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(banKey))) {
                Long ttl = redisTemplate.getExpire(banKey);
                long retryAfter = (ttl == null || ttl < 0) ? 1 : Math.max(1, ttl);
                throw new RuntimeException("请求过于频繁，请 " + retryAfter + " 秒后重试");
            }

            String appIpRateKey = bucketKey("cg:tp:card:rate:appip", appIpKey, now, APP_IP_WINDOW_MS);
            long appIpCount = incrementWithExpire(appIpRateKey, APP_IP_WINDOW_MS);
            if (appIpCount > APP_IP_MAX_PER_MINUTE) {
                throw new RuntimeException("请求过于频繁，请稍后重试");
            }

            if (StringUtils.hasText(cardCode)) {
                String appIpCardKey = buildAppIpCardKey(appId, ip, cardCode);
                String appIpCardRateKey = bucketKey("cg:tp:card:rate:appipcard", appIpCardKey, now, APP_IP_CARD_WINDOW_MS);
                long appIpCardCount = incrementWithExpire(appIpCardRateKey, APP_IP_CARD_WINDOW_MS);
                if (appIpCardCount > APP_IP_CARD_MAX_PER_MINUTE) {
                    throw new RuntimeException("请求过于频繁，请稍后重试");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // 限流组件异常时降级放行，避免影响主业务可用性
        }
    }

    public void markResult(Long appId, String ip, boolean success) {
        try {
            String appIpKey = buildAppIpKey(appId, ip);
            String failKey = "cg:tp:card:fail:" + appIpKey;
            String banKey = "cg:tp:card:ban:" + appIpKey;

            if (success) {
                redisTemplate.delete(failKey);
                return;
            }

            long failCount = incrementWithExpire(failKey, FAIL_STREAK_WINDOW_MS);
            if (failCount >= FAIL_STREAK_THRESHOLD) {
                redisTemplate.opsForValue().set(banKey, "1", Duration.ofMillis(BAN_MS));
                redisTemplate.delete(failKey);
            }
        } catch (Exception e) {
            // 失败记录异常时降级忽略
        }
    }

    private String buildAppIpKey(Long appId, String ip) {
        return appId + "|" + (ip == null ? "unknown-ip" : ip.trim());
    }

    private String buildAppIpCardKey(Long appId, String ip, String cardCode) {
        String normCard = cardCode == null ? "" : cardCode.trim().toUpperCase();
        return buildAppIpKey(appId, ip) + "|" + normCard;
    }

    private String bucketKey(String prefix, String dimKey, long nowMs, long windowMs) {
        long bucket = nowMs / windowMs;
        return String.format(Locale.ROOT, "%s:%s:%d", prefix, dimKey, bucket);
    }

    private long incrementWithExpire(String key, long ttlMs) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMillis(ttlMs));
        }
        return count == null ? 0L : count;
    }
}

