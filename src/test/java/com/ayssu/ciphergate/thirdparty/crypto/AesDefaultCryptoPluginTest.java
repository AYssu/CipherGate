package com.ayssu.ciphergate.thirdparty.crypto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.symmetric.AES;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesDefaultCryptoPluginTest {

    @Test
    void decrypt_data_hex_to_map_matches_encryption_module_style() {
        String key = "cg_demo_key_1234";
        String canonical = "cardCode=ABC123&deviceId=DEV-1&timestamp=1710000000000";
        AES aes = new AES(StrUtil.bytes(key));
        String dataHex = aes.encryptHex(canonical);

        Map<String, Object> input = new HashMap<>();
        input.put("data", dataHex);
        Map<String, Object> encCfg = new HashMap<>();
        encCfg.put("aesKey", key);
        input.put("encryptionConfig", encCfg);

        AesDefaultCryptoPlugin plugin = new AesDefaultCryptoPlugin();
        Map<String, Object> out = plugin.decryptToMap(input);

        assertFalse(out.isEmpty());
        assertEquals("ABC123", out.get("cardCode"));
        assertEquals("DEV-1", out.get("deviceId"));
        assertEquals("1710000000000", out.get("timestamp"));
    }

    @Test
    void encrypt_then_decrypt_roundtrip() {
        String key = "cg_demo_key_1234";
        Map<String, Object> plain = new HashMap<>();
        plain.put("cardCode", "ABC123");
        plain.put("deviceId", "DEV-1");
        plain.put("timestamp", "1710000000000");
        Map<String, Object> encCfg = new HashMap<>();
        encCfg.put("aesKey", key);
        plain.put("encryptionConfig", encCfg);

        AesDefaultCryptoPlugin plugin = new AesDefaultCryptoPlugin();
        String dataHex = plugin.encryptFromMap(plain);

        Map<String, Object> input = new HashMap<>();
        input.put("data", dataHex);
        input.put("encryptionConfig", encCfg);

        Map<String, Object> out = plugin.decryptToMap(input);
        assertFalse(out.isEmpty());
        assertEquals("ABC123", out.get("cardCode"));
        assertEquals("DEV-1", out.get("deviceId"));
        assertEquals("1710000000000", out.get("timestamp"));
    }

    /** 与 EncryptionModule 测试用固定密文一致，依赖 classpath aes-default.defaults.json 中的演示密钥 */
    @Test
    void decrypt_fixed_hex_uses_packaged_defaults() {
        String dataHex =
                "8b3024625c7d569f5022cf33423bb3448733ef41159b4e3436e32198a247680a"
                        + "73d88cf2fac783e5f3913743c963f35258a3c5184dd7d804d4f00d622afd58e6"
                        + "b9bcd0e536d47a62e1b2442a511fd32f";

        Map<String, Object> input = new HashMap<>();
        input.put("data", dataHex);

        AesDefaultCryptoPlugin plugin = new AesDefaultCryptoPlugin();
        Map<String, Object> out = plugin.decryptToMap(input);

        assertNotNull(out);
        assertFalse(out.isEmpty());
        assertTrue(out.containsKey("cardCode"));
        assertTrue(out.containsKey("deviceId"));
        assertTrue(out.containsKey("timestamp"));
    }
}
