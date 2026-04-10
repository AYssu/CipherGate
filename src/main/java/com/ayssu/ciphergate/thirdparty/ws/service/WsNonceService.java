package com.ayssu.ciphergate.thirdparty.ws.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WsNonceService {
    private static final Duration TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;

    public WsNonceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean markIfNew(String scope, String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        String key = "cg:ws:nonce:" + (scope == null ? "default" : scope) + ":" + nonce;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
        return ok != null && ok;
    }
}

