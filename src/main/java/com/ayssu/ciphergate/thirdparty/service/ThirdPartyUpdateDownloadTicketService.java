package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 更新包下载短时票据：HMAC-SHA256(appSecret, 载荷)，有效期 5 分钟，无 Redis。
 */
@Service
public class ThirdPartyUpdateDownloadTicketService {

    private static final int TICKET_TTL_MINUTES = 5;
    private static final int NONCE_BYTES = 16;

    private final SecureRandom rng = new SecureRandom();

    public String mint(long appId, String appSecret) {
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("appSecret 为空，无法签发下载票据");
        }
        long expMs = Instant.now().toEpochMilli() + TimeUnit.MINUTES.toMillis(TICKET_TTL_MINUTES);
        byte[] nonce = new byte[NONCE_BYTES];
        rng.nextBytes(nonce);
        String nonceHex = HexFormat.of().formatHex(nonce);
        String body = appId + "|" + expMs + "|" + nonceHex;
        String sig = ThirdPartySignatureVerifier.hmacSha256Hex(appSecret, body);
        String token = body + "|" + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验票据格式、过期与 HMAC；返回载荷中的 appId。
     */
    public Optional<Long> verify(String ticketB64, String appSecret) {
        if (!StringUtils.hasText(ticketB64) || !StringUtils.hasText(appSecret)) {
            return Optional.empty();
        }
        String token;
        try {
            token = new String(Base64.getUrlDecoder().decode(ticketB64.trim()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Optional.empty();
        }
        String[] parts = token.split("\\|", 4);
        if (parts.length != 4) {
            return Optional.empty();
        }
        long appId;
        long expMs;
        try {
            appId = Long.parseLong(parts[0]);
            expMs = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().toEpochMilli() > expMs) {
            return Optional.empty();
        }
        String body = parts[0] + "|" + parts[1] + "|" + parts[2];
        String expect = ThirdPartySignatureVerifier.hmacSha256Hex(appSecret, body);
        if (!expect.equalsIgnoreCase(parts[3])) {
            return Optional.empty();
        }
        return Optional.of(appId);
    }
}
