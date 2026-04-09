package com.ayssu.ciphergate.thirdparty.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReplayProtectionService {
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_ENTRIES = 200_000;

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    public boolean checkAndMark(String appKey, String nonce, long timestampMs, long nowMs) {
        cleanupIfNeeded(nowMs);

        String key = appKey + ":" + nonce;
        Long existing = seen.putIfAbsent(key, nowMs);
        if (existing != null) {
            return false;
        }

        // basic skew check (window handled elsewhere too)
        long age = Math.abs(nowMs - timestampMs);
        return age <= DEFAULT_TTL_MS;
    }

    private void cleanupIfNeeded(long nowMs) {
        if (seen.size() < MAX_ENTRIES) {
            return;
        }
        long cutoff = nowMs - DEFAULT_TTL_MS;
        for (Map.Entry<String, Long> e : seen.entrySet()) {
            Long v = e.getValue();
            if (v == null || v < cutoff) {
                seen.remove(e.getKey(), v);
            }
        }
    }
}

