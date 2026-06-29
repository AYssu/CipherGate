package com.ayssu.ciphergate.thirdparty.crypto;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 {@code EncryptionModule} 中 {@code AesDataCryptoPlugin} 对齐的默认实现：
 * <ul>
 *   <li>{@code data} 为 Hutool AES 加密后的 <strong>十六进制</strong>字符串</li>
 *   <li>明文字符串为 canonical：{@code key=value&key2=value2}（加密前对 key 排序）</li>
 *   <li>密钥长度须为 16 / 24 / 32 字节（UTF-8）</li>
 * </ul>
 * 密钥来源顺序：应用 {@code encryptionConfig.aesKey}（或 {@code secretKey}）→ {@code pluginConfig.aesKey} →
 * classpath {@code aes-default.defaults.json}。
 */
public class AesDefaultCryptoPlugin implements CryptoPluginEncryptor {

    private static final String PLUGIN_ID = "aes-default";
    private static final String DEFAULTS_RESOURCE = "aes-default.defaults.json";

    @Override
    public String pluginId() {
        return PLUGIN_ID;
    }

    @Override
    public Map<String, Object> decryptToMap(Map<String, Object> input) {
        Object dataObj = input == null ? null : input.get("data");
        if (!(dataObj instanceof String data) || !StringUtils.hasText(data)) {
            return Map.of();
        }

        AES aes = new AES(StrUtil.bytes(resolveAesKey(input)));
        byte[] cipherBytes = HexUtil.decodeHex(data.trim());
        String canonical = new String(aes.decrypt(cipherBytes), StandardCharsets.UTF_8);
        if (!StringUtils.hasText(canonical)) {
            return Map.of();
        }

        return parseCanonical(canonical);
    }

    @Override
    public String encryptFromMap(Map<String, Object> plain) {
        String canonical = buildCanonical(plain);
        if (!StringUtils.hasText(canonical)) {
            return "";
        }
        AES aes = new AES(StrUtil.bytes(resolveAesKey(plain)));
        return aes.encryptHex(canonical);
    }

    @SuppressWarnings("unchecked")
    private String resolveAesKey(Map<String, Object> input) {
        // 1) 应用 encryptionConfig（与 EncryptionModule 的 pluginConfig 区分）
        Object encObj = input == null ? null : input.get("encryptionConfig");
        if (encObj instanceof Map<?, ?> enc) {
            Object v = enc.get("aesKey");
            if (v == null) {
                v = enc.get("secretKey");
            }
            if (v instanceof String s && StringUtils.hasText(s)) {
                validateAesKey(s);
                return s;
            }
        }

        // 2) 插件库表 pluginConfig（与 EncryptionModule 一致）
        Object cfgObj = input == null ? null : input.get("pluginConfig");
        if (cfgObj instanceof Map<?, ?> cfg) {
            Object v = cfg.get("aesKey");
            if (v instanceof String s && StringUtils.hasText(s)) {
                validateAesKey(s);
                return s;
            }
        }

        // 3) 打包默认（与 EncryptionModule 的 plugin-config.defaults.json 用途相同）
        String fromFile = readAesKeyFromDefaultsFile();
        if (StringUtils.hasText(fromFile)) {
            validateAesKey(fromFile);
            return fromFile;
        }

        throw new IllegalArgumentException("请在应用加密配置(encryptionConfig)或插件配置(pluginConfig)中设置 aesKey（16/24/32 字节）");
    }

    private String readAesKeyFromDefaultsFile() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (in == null) {
                return null;
            }
            String json = new String(in.readAllBytes(), Charset.forName("UTF-8"));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            JSONObject obj = JSONUtil.parseObj(json);
            String aesKey = obj.getStr("aesKey");
            return StringUtils.hasText(aesKey) ? aesKey : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateAesKey(String key) {
        int len = key == null ? 0 : key.getBytes(StandardCharsets.UTF_8).length;
        if (len != 16 && len != 24 && len != 32) {
            throw new IllegalArgumentException("aesKey长度必须是16/24/32字节");
        }
    }

    private Map<String, Object> parseCanonical(String canonical) {
        Map<String, Object> out = new LinkedHashMap<>();
        String[] pairs = canonical.split("&");
        for (String pair : pairs) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);
            out.put(key, value);
        }
        return out;
    }

    private String buildCanonical(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (!StringUtils.hasText(k)) {
                continue;
            }
            Object v = map.get(k);
            if (v == null) {
                continue;
            }
            if ("data".equals(k) || "encryptionConfig".equals(k) || "pluginConfig".equals(k)) {
                continue;
            }
            sb.append(k).append("=").append(v).append("&");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
