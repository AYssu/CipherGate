package com.ciphergate.plugin.rsa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RsaCryptoPluginTest {
    private static KeyPair keyPair;
    private final RsaCryptoPlugin plugin = new RsaCryptoPlugin();

    @BeforeAll static void generateTestKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test void hybridRoundTripPreservesUnicodeAndReservedCharactersWithoutExposingConfiguration() {
        String encrypted = plugin.encryptFromMap(outbound(Map.of("name", "更新包 + & = 中文", "count", 42)));
        assertEquals(Map.of("name", "更新包 + & = 中文", "count", "42"), decrypt(encrypted, keyPair));
        assertFalse(encrypted.contains("更新包"));
    }

    @Test void repeatedEncryptionUsesFreshKeysAndNonces() {
        Map<String, Object> plain = outbound(Map.of("message", "same payload"));
        String first = plugin.encryptFromMap(plain), second = plugin.encryptFromMap(plain);
        assertNotEquals(first, second);
        assertEquals(decrypt(first, keyPair), decrypt(second, keyPair));
    }

    @Test void tamperedCiphertextFailsAuthentication() {
        String encrypted = plugin.encryptFromMap(outbound(Map.of("message", "payload")));
        String[] parts = encrypted.split("\\.");
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        ciphertext[ciphertext.length - 1] ^= 1;
        String tampered = parts[0] + "." + Base64.getEncoder().encodeToString(ciphertext);
        assertThrows(CryptoDecryptionException.class, () -> decrypt(tampered, keyPair));
    }

    @Test void wrongPrivateKeyCannotDecrypt() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var otherKeys = generator.generateKeyPair();
        String encrypted = plugin.encryptFromMap(outbound(Map.of("message", "payload")));
        assertThrows(CryptoDecryptionException.class, () -> decrypt(encrypted, otherKeys));
    }

    @Test void missingKeysAndMalformedFormatsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> plugin.encryptFromMap(Map.of("message", "payload")));
        assertThrows(IllegalArgumentException.class, () -> plugin.decryptToMap(Map.of("data", "a.b")));
        assertThrows(IllegalArgumentException.class, () -> decrypt("missing-delimiter", keyPair));
        assertThrows(RuntimeException.class, () -> decrypt("not-valid-base64.***", keyPair));
    }

    @Test void emptyInputHasNoEncryptedPayload() {
        assertEquals("", plugin.encryptFromMap(Map.of()));
        assertEquals(Map.of(), plugin.decryptToMap(Map.of()));
        assertEquals("rsa-crypto", plugin.pluginId());
    }

    private Map<String, Object> outbound(Map<String, ?> values) {
        Map<String, Object> plain = new HashMap<>(values);
        plain.put("pluginConfig", Map.of("clientPublicKey", Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded())));
        return plain;
    }

    private Map<String, Object> decrypt(String data, KeyPair keys) {
        return plugin.decryptToMap(Map.of("data", data, "pluginConfig", Map.of("serverPrivateKey",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()))));
    }
}
