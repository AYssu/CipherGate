package com.ayssu.ciphergate.thirdparty.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ThirdPartySignatureVerifier {
    private ThirdPartySignatureVerifier() {
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            return toHex(dig);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    public static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return toHex(sig);
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA256 unavailable", e);
        }
    }

    public static String buildSignString(String method, String path, String timestamp, String nonce, String bodyDigestHex) {
        return method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigestHex;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) {
            sb.append(Character.forDigit((v >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }
}

