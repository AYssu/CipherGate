package com.ayssu.ciphergate.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录限流器：防止暴力破解。
 * <p>
 * 策略：同一账号或 IP 在窗口期内失败次数达到上限后锁定，拒绝后续请求。
 */
@Component
public class LoginRateLimiter {

    /** 单次锁定时长（秒） */
    private static final long LOCK_DURATION_SECONDS = 15 * 60; // 15 分钟

    /** 允许的最大失败次数 */
    private static final int MAX_FAILURES = 5;

    /** 记录 key → 失败信息 */
    private final ConcurrentHashMap<String, FailureRecord> failures = new ConcurrentHashMap<>();

    /**
     * 检查指定 key 是否被锁定
     * @param key 账号名或 IP
     * @return 剩余锁定秒数；0 表示未锁定
     */
    public long getLockRemainingSeconds(String key) {
        FailureRecord record = failures.get(key);
        if (record == null) return 0;

        long elapsed = (System.currentTimeMillis() - record.lastFailureTime) / 1000;
        if (elapsed >= LOCK_DURATION_SECONDS) {
            failures.remove(key);
            return 0;
        }
        if (record.failures >= MAX_FAILURES) {
            return LOCK_DURATION_SECONDS - elapsed;
        }
        return 0;
    }

    /**
     * 记录一次失败尝试
     * @param key 账号名或 IP
     */
    public void recordFailure(String key) {
        failures.merge(key, new FailureRecord(1), (old, ignored) -> {
            long elapsed = (System.currentTimeMillis() - old.lastFailureTime) / 1000;
            if (elapsed >= LOCK_DURATION_SECONDS) {
                return new FailureRecord(1);
            }
            old.failures++;
            old.lastFailureTime = System.currentTimeMillis();
            return old;
        });
    }

    /**
     * 登录成功时清除失败记录
     * @param key 账号名或 IP
     */
    public void clearFailures(String key) {
        failures.remove(key);
    }

    private static class FailureRecord {
        int failures;
        long lastFailureTime;

        FailureRecord(int failures) {
            this.failures = failures;
            this.lastFailureTime = System.currentTimeMillis();
        }
    }
}
