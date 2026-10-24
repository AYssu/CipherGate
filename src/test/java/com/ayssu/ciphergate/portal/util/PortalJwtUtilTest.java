package com.ayssu.ciphergate.portal.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class PortalJwtUtilTest {
    public static final String TEST_SECRET = "ciphergate-unit-test-secret-for-jwt-only-123456789";
    private final PortalJwtUtil jwt = new PortalJwtUtil(TEST_SECRET, 60_000);

    @Test void missingAndWeakSecretsCannotBePaddedIntoSigningKeys() {
        for (String secret : new String[]{null, "", " ", "1234567890123456789012345678901",
                "CipherGatePortalJwtSecret2026!@#$%^&*()_+AbCdEfGhIjKlMnOpQrStUvWxYz"}) {
            assertThrows(IllegalArgumentException.class, () -> new PortalJwtUtil(secret, 60_000));
        }
    }

    @Test void missingConfigurationFailsDuringBeanCreation() {
        new ApplicationContextRunner().withUserConfiguration(PortalJwtUtil.class).run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage().contains("PortalJwtUtil"));
        });
    }

    @Test void nonPositiveExpirationFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new PortalJwtUtil(TEST_SECRET, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortalJwtUtil(TEST_SECRET, -1));
    }

    @Test void validTokenRoundTripsIdentityAndExpiry() {
        String token = jwt.generateToken(41L, 12L, "member@example.test", "Member");
        assertTrue(jwt.validateToken(token));
        assertEquals(41L, jwt.getUserIdFromToken(token));
        assertEquals(12L, jwt.getAppIdFromToken(token));
        assertEquals("member@example.test", jwt.getEmailFromToken(token));
        assertEquals("Member", jwt.parseToken(token).get("nickname"));
        assertEquals(60_000L, jwt.parseToken(token).getExpiration().getTime()
                - jwt.parseToken(token).getIssuedAt().getTime());
    }

    @Test void differentServerKeysCannotValidateEachOthersTokens() {
        var other = new PortalJwtUtil("another-server-independent-signing-secret-123456789", 60_000);
        assertFalse(other.validateToken(jwt.generateToken(41L, 12L, "member@example.test", "Member")));
    }

    @Test void tamperingMalformedAndUnsignedTokensAreRejected() {
        String token = jwt.generateToken(41L, 12L, "member@example.test", "Member");
        String[] parts = token.split("\\.");
        String replacement = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"appId\":1}".getBytes(StandardCharsets.UTF_8));
        assertFalse(jwt.validateToken(parts[0] + "." + replacement + "." + parts[2]));
        assertFalse(jwt.validateToken("not-a-jwt"));
        assertFalse(jwt.validateToken(Jwts.builder().subject("1").compact()));
    }

    @Test void expiredTokenCannotAuthenticate() {
        String expired = Jwts.builder().subject("41").claim("appId", 12L)
                .issuedAt(new Date(0)).expiration(new Date(1000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertFalse(jwt.validateToken(expired));
    }
}
