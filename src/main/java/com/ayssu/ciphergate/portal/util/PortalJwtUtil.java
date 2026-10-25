package com.ayssu.ciphergate.portal.util;

import io.jsonwebtoken.Claims;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.service.PublicAbuseGuard;
import java.util.List;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PortalJwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public PortalJwtUtil(@Value("${portal.jwt.secret:}") String secret,
                         @Value("${portal.jwt.expiration:86400000}") long expirationMs) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.strip().equals("CipherGatePortalJwtSecret2026!@#$%^&*()_+AbCdEfGhIjKlMnOpQrStUvWxYz")) {
            throw new IllegalArgumentException("PORTAL_JWT_SECRET must be an independent secret of at least 32 UTF-8 bytes; the historical default is forbidden");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("portal.jwt.expiration must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long appUserId, Long appId, String email, String nickname) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("appId", appId);
        claims.put("email", email);
        claims.put("nickname", nickname);

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(appUserId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Fingerprint revokes sessions immediately after a credential/email change, without storing hashes in JWTs. */
    public String credentialStamp(AppUser user) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return java.util.HexFormat.of().formatHex(mac.doFinal((user.getId() + ":" + user.getAppId() + ":" + user.getEmail() + ":" + user.getPassword()).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public String generateSessionToken(AppUser user) {
        return issue(user.getId(), Map.of("appId", user.getAppId(), "email", user.getEmail(),
            "nickname", user.getNickname() == null ? "" : user.getNickname(), "purpose", "SESSION", "credential", credentialStamp(user)), expirationMs);
    }

    public String generateSelectionToken(String email, List<AppUser> verifiedUsers) {
        Map<String, String> grants = new HashMap<>();
        for (AppUser user : verifiedUsers) grants.put(user.getId().toString(), credentialStamp(user));
        return issue(0L, Map.of("appId", 0L, "email", email, "purpose", "SELECT_APP", "grants", grants), Math.min(expirationMs, 300_000));
    }

    private String issue(Long userId, Map<String, ?> claims, long lifetime) {
        Date now = new Date();
        return Jwts.builder().claims(claims).subject(userId.toString()).issuedAt(now)
            .expiration(new Date(now.getTime() + lifetime)).signWith(signingKey).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public Long getAppIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("appId", Long.class);
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Portal JWT token expired");
            return false;
        } catch (Exception e) {
            log.debug("Portal JWT token invalid");
            return false;
        }
    }
}
