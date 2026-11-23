package com.ayssu.ciphergate.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Version 1: sorted RFC3986 UTF-8 fields. Null omitted; empty values signed. */
public final class SkPaymentSignature {
    public static final String ALGORITHM = "HMAC-SHA256";
    private SkPaymentSignature() {}
    public static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~"); }
    public static String canonical(Map<String,String> params) {
        StringJoiner joined = new StringJoiner("&");
        new TreeMap<>(params).forEach((k,v) -> { if (!"sign".equals(k) && v != null) joined.add(encode(k)+"="+encode(v)); });
        return joined.toString();
    }
    public static String sign(Map<String,String> params, String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Signing secret required");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(params).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static void seal(Map<String,String> params, String secret, String purpose) {
        params.put("sign_type", ALGORITHM); params.put("purpose",purpose);
        params.put("timestamp", Long.toString(Instant.now().getEpochSecond()));
        params.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        params.put("sign", sign(params,secret));
    }
    public static boolean verify(Map<String,String> p, String key, String purpose, long now) {
        try {
            if (!ALGORITHM.equals(p.get("sign_type")) || !purpose.equals(p.get("purpose")) || !p.getOrDefault("nonce", "").matches("[A-Za-z0-9]{16,64}") || !p.getOrDefault("sign", "").matches("[0-9a-fA-F]{64}")) return false;
            long ts=Long.parseLong(p.get("timestamp"));
            if (ts < now-300 || ts > now+300) return false;
            return MessageDigest.isEqual(HexFormat.of().parseHex(sign(p,key)), HexFormat.of().parseHex(p.get("sign")));
        } catch (RuntimeException e) { return false; }
    }
}
