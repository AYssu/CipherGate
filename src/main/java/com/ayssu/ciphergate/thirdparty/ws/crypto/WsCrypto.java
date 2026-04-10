package com.ayssu.ciphergate.thirdparty.ws.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Base64;

public final class WsCrypto {
    private WsCrypto() {}

    public static KeyPair generateX25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("X25519 unavailable", e);
        }
    }

    public static PublicKey decodeX25519PublicKey(byte[] x509Bytes) {
        try {
            return java.security.KeyFactory.getInstance("X25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(x509Bytes));
        } catch (Exception e) {
            throw new RuntimeException("Bad public key", e);
        }
    }

    public static byte[] ecdhX25519(KeyPair local, PublicKey remotePublic) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(local.getPrivate());
            ka.doPhase(remotePublic, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new RuntimeException("ECDH failed", e);
        }
    }

    /**
     * HKDF-SHA256.
     */
    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int len) {
        byte[] prk = hmacSha256(salt == null ? new byte[32] : salt, ikm);
        byte[] okm = new byte[len];
        byte[] t = new byte[0];
        int offset = 0;
        int counter = 1;
        while (offset < len) {
            byte[] data = concat(t, info == null ? new byte[0] : info, new byte[]{(byte) counter});
            t = hmacSha256(prk, data);
            int copy = Math.min(t.length, len - offset);
            System.arraycopy(t, 0, okm, offset, copy);
            offset += copy;
            counter++;
        }
        return okm;
    }

    public static byte[] hmacSha256(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg);
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA256 unavailable", e);
        }
    }

    public static AesGcmPack aesGcmEncrypt(byte[] key32, byte[] plain, byte[] aad) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            if (aad != null && aad.length > 0) {
                c.updateAAD(aad);
            }
            byte[] ctWithTag = c.doFinal(plain);
            int tagLen = 16;
            byte[] ct = Arrays.copyOfRange(ctWithTag, 0, ctWithTag.length - tagLen);
            byte[] tag = Arrays.copyOfRange(ctWithTag, ctWithTag.length - tagLen, ctWithTag.length);
            return new AesGcmPack(iv, ct, tag);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] aesGcmDecrypt(byte[] key32, byte[] iv, byte[] ciphertext, byte[] tag, byte[] aad) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            if (aad != null && aad.length > 0) {
                c.updateAAD(aad);
            }
            byte[] ctWithTag = concat(ciphertext, tag);
            return c.doFinal(ctWithTag);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decrypt failed", e);
        }
    }

    public static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static byte[] b64d(String s) {
        return Base64.getDecoder().decode(s);
    }

    public static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += (p == null ? 0 : p.length);
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            if (p == null || p.length == 0) continue;
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    public record AesGcmPack(byte[] iv, byte[] ciphertext, byte[] tag) {}
}

