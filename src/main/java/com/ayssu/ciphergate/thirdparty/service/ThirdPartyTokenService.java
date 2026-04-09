package com.ayssu.ciphergate.thirdparty.service;

import lombok.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThirdPartyTokenService {
    private static final long TOKEN_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 200_000;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public IssuedToken issue(Long appId, Long userId, Long bindId) {
        cleanupIfNeeded(Instant.now().toEpochMilli());
        long now = Instant.now().toEpochMilli();
        long exp = now + TOKEN_TTL_MS;

        String token = generateToken();
        sessions.put(token, new Session(appId, userId, bindId, exp));
        return new IssuedToken(token, toLocalDateTime(exp));
    }

    public Session getValid(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        long now = Instant.now().toEpochMilli();
        Session s = sessions.get(token.trim());
        if (s == null) {
            return null;
        }
        if (s.expiresAtMs <= now) {
            sessions.remove(token.trim(), s);
            return null;
        }
        return s;
    }

    public LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private String generateToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private void cleanupIfNeeded(long nowMs) {
        if (sessions.size() < MAX_ENTRIES) {
            return;
        }
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            if (s == null || s.expiresAtMs <= nowMs) {
                sessions.remove(e.getKey(), s);
            }
        }
    }

    @Value
    public static class Session {
        Long appId;
        Long userId;
        Long bindId;
        long expiresAtMs;
    }

    @Value
    public static class IssuedToken {
        String accessToken;
        LocalDateTime expiresAt;
    }
}

