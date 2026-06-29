package com.ciphergate.plugin.rsa;

import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSA + AES 混合加密插件。
 * <p>
 * 加密（出站）：随机 AES 密钥 → AES-GCM 加密数据 → RSA-OAEP 加密 AES 密钥 → 返回组合密文
 * 解密（入站）：RSA-OAEP 解密得到 AES 密钥 → AES-GCM 解密数据 → 解析 canonical
 * <p>
 * 传输格式（. 分隔）：base64(RSA加密的AES密钥) . base64(AES-GCM密文)
 * AES-GCM 密文 = IV(12B) + ciphertext + tag(16B)
 * <p>
 * 客户端需注意：RSA 填充模式已从 PKCS1Padding 升级为 OAEPWithSHA-256AndMGF1Padding，
 * 客户端加密时必须使用相同的 OAEP 填充模式。
 */
@Extension
public class RsaCryptoPlugin implements CryptoPluginEncryptorExt {

    private static final Logger log = LoggerFactory.getLogger(RsaCryptoPlugin.class);

    private static final String PLUGIN_ID = "rsa-crypto";
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_SIZE = 128;

    // ========== 缓存：密钥对象 ==========
    private static final ConcurrentHashMap<String, PrivateKey> PRIVATE_KEY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PublicKey> PUBLIC_KEY_CACHE = new ConcurrentHashMap<>();

    // ========== 缓存：共享 SecureRandom 和 KeyGenerator ==========
    private static final SecureRandom SHARED_RANDOM = new SecureRandom();
    private static final KeyGenerator AES_KEY_GEN;
    static {
        try {
            AES_KEY_GEN = KeyGenerator.getInstance("AES");
            AES_KEY_GEN.init(AES_KEY_SIZE, SHARED_RANDOM);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("AES KeyGenerator 初始化失败: " + e.getMessage());
        }
    }

    // ========== ThreadLocal Cipher 实例（Cipher 非线程安全） ==========
    private static final ThreadLocal<Cipher> RSA_CIPHER_TL = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(RSA_OAEP);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("RSA Cipher 初始化失败: " + e.getMessage());
        }
    });
    private static final ThreadLocal<Cipher> AES_CIPHER_TL = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(AES_GCM);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("AES Cipher 初始化失败: " + e.getMessage());
        }
    });

    // ========== 从 canonical 中排除的保留 key ==========
    private static final java.util.Set<String> RESERVED_KEYS = java.util.Set.of("data", "encryptionConfig", "pluginConfig");

    @Override
    public String pluginId() {
        return PLUGIN_ID;
    }

    /**
     * 入站解密（混合）：
     * 1. 从 data 中拆分 RSA 密文和 AES 密文
     * 2. RSA-OAEP 私钥解密得到 AES 密钥
     * 3. AES-GCM 解密数据
     * 4. 解析 canonical key=value& Map
     */
    @Override
    public Map<String, Object> decryptToMap(Map<String, Object> input) {
        String data = getString(input, "data");
        if (data == null || data.isBlank()) {
            return Map.of();
        }

        String privateKeyPem = resolveKey(input, "serverPrivateKey", "privateKey");
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalArgumentException("缺少 serverPrivateKey 配置");
        }

        try {
            // 拆分: base64(RSA密文).base64(AES密文)
            int dotIdx = data.indexOf('.');
            if (dotIdx < 0) {
                throw new IllegalArgumentException("密文格式错误：缺少分隔符 '.'");
            }
            String rsaPart = data.substring(0, dotIdx);
            String aesPart = data.substring(dotIdx + 1);

            // RSA-OAEP 解密得到 AES 密钥
            PrivateKey privateKey = loadPrivateKey(privateKeyPem);
            Cipher rsaCipher = RSA_CIPHER_TL.get();
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(Base64.getDecoder().decode(rsaPart));

            // AES-GCM 解密数据
            // 格式: IV(12B) + ciphertext + tag(16B)
            // Java GCM doFinal 接受 ciphertext+tag（tag 追加在密文后面）
            byte[] aesFull = Base64.getDecoder().decode(aesPart);
            int tagLen = GCM_TAG_SIZE / 8; // 16
            if (aesFull.length < GCM_IV_SIZE + tagLen) {
                throw new IllegalArgumentException("AES 密文太短: " + aesFull.length + " bytes, 最少需要 " + (GCM_IV_SIZE + tagLen));
            }
            byte[] iv = new byte[GCM_IV_SIZE];
            System.arraycopy(aesFull, 0, iv, 0, GCM_IV_SIZE);
            // 剩余部分 = ciphertext + tag，直接传给 doFinal
            byte[] ciphertextWithTag = new byte[aesFull.length - GCM_IV_SIZE];
            System.arraycopy(aesFull, GCM_IV_SIZE, ciphertextWithTag, 0, ciphertextWithTag.length);

            SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            Cipher aesCipher = AES_CIPHER_TL.get();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
            byte[] decrypted = aesCipher.doFinal(ciphertextWithTag);
            String plainText = new String(decrypted, StandardCharsets.UTF_8);

            return parseCanonical(plainText);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoDecryptionException("混合解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 出站加密（混合）：
     * 1. 生成随机 AES-256 密钥
     * 2. AES-GCM 加密 canonical 数据
     * 3. RSA-OAEP 公钥加密 AES 密钥
     * 4. 返回 base64(RSA密文).base64(IV + ciphertext + tag)
     */
    @Override
    public String encryptFromMap(Map<String, Object> plain) {
        String canonical = buildCanonical(plain);
        if (canonical.isBlank()) {
            return "";
        }

        String publicKeyPem = resolveKey(plain, "clientPublicKey", "publicKey");
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalArgumentException("缺少 clientPublicKey 配置");
        }

        try {
            // 1. 生成随机 AES 密钥（复用缓存的 KeyGenerator）
            byte[] aesKeyBytes = AES_KEY_GEN.generateKey().getEncoded();

            // 2. AES-GCM 加密数据
            byte[] iv = new byte[GCM_IV_SIZE];
            SHARED_RANDOM.nextBytes(iv);
            SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            Cipher aesCipher = AES_CIPHER_TL.get();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
            byte[] aesEncrypted = aesCipher.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            // aesEncrypted = ciphertext + tag(16B)

            // 3. 组合 AES 密文: IV + ciphertext + tag
            byte[] aesFull = new byte[iv.length + aesEncrypted.length];
            System.arraycopy(iv, 0, aesFull, 0, iv.length);
            System.arraycopy(aesEncrypted, 0, aesFull, iv.length, aesEncrypted.length);
            String aesPart = Base64.getEncoder().encodeToString(aesFull);

            // 4. RSA-OAEP 加密 AES 密钥
            PublicKey publicKey = loadPublicKey(publicKeyPem);
            Cipher rsaCipher = RSA_CIPHER_TL.get();
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] rsaEncrypted = rsaCipher.doFinal(aesKeyBytes);
            String rsaPart = Base64.getEncoder().encodeToString(rsaEncrypted);

            // 5. 返回组合密文: base64(RSA密文).base64(IV+ciphertext+tag)
            return rsaPart + "." + aesPart;
        } catch (Exception e) {
            throw new CryptoEncryptionException("混合加密失败: " + e.getMessage(), e);
        }
    }

    // ========== 密钥解析 ==========

    private String resolveKey(Map<String, Object> input, String... candidates) {
        Object encObj = input.get("encryptionConfig");
        if (encObj instanceof Map<?, ?> enc) {
            for (String key : candidates) {
                Object v = enc.get(key);
                if (v instanceof String s && !s.isBlank()) return s;
            }
        }
        Object cfgObj = input.get("pluginConfig");
        if (cfgObj instanceof Map<?, ?> cfg) {
            for (String key : candidates) {
                Object v = cfg.get(key);
                if (v instanceof String s && !s.isBlank()) return s;
            }
        }
        for (String key : candidates) {
            Object v = input.get(key);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    /**
     * 加载 RSA 私钥，结果缓存避免重复解析 PEM。
     */
    private PrivateKey loadPrivateKey(String pem) throws Exception {
        return PRIVATE_KEY_CACHE.computeIfAbsent(pem, key -> {
            try {
                String base64 = key
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                        .replace("-----END RSA PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] keyBytes = Base64.getDecoder().decode(base64);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(spec);
            } catch (Exception e) {
                throw new CryptoDecryptionException("RSA 私钥加载失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 加载 RSA 公钥，结果缓存避免重复解析 PEM。
     */
    private PublicKey loadPublicKey(String pem) throws Exception {
        return PUBLIC_KEY_CACHE.computeIfAbsent(pem, key -> {
            try {
                String base64 = key
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
                byte[] keyBytes = Base64.getDecoder().decode(base64);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(spec);
            } catch (Exception e) {
                throw new CryptoEncryptionException("RSA 公钥加载失败: " + e.getMessage(), e);
            }
        });
    }

    // ========== Canonical 格式（URL 编码） ==========

    /**
     * 解析 canonical 格式，key 和 value 均经过 URL decode。
     */
    private Map<String, Object> parseCanonical(String canonical) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (canonical == null || canonical.isBlank()) return out;
        for (String pair : canonical.split("&")) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            out.put(key, value);
        }
        return out;
    }

    /**
     * 构建 canonical 格式，key 和 value 均经过 URL encode。
     */
    private String buildCanonical(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "";
        var keys = new java.util.ArrayList<>(map.keySet());
        keys.sort(java.util.Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            Object v = map.get(k);
            if (v == null) continue;
            if (RESERVED_KEYS.contains(k)) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }
}
