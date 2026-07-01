package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.VariableSecurityTier;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.service.AppVariableTemplateContext;
import com.ayssu.ciphergate.thirdparty.service.AppVariableTemplateResolver;
import com.ayssu.ciphergate.thirdparty.ws.crypto.WsCrypto;
import com.ayssu.ciphergate.thirdparty.ws.model.WsCipher;
import com.ayssu.ciphergate.thirdparty.ws.model.WsEnvelope;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡密 WebSocket 心跳服务。
 * 每 30 秒向所有已连接的卡密 WS 会话发送加密 HEARTBEAT（含变量）。
 * 需要客户端先完成 HELLO 握手建立 sessionKey。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardWsHeartbeatService {

    private static final long INTERVAL_MS = 30_000L;

    private static final String ATTR_CARD_ID = "cg.card.ws.cardId";
    private static final String ATTR_APP_ID = "cg.card.ws.appId";
    private static final String ATTR_CONN_ID = "cg.card.ws.connId";
    private static final String ATTR_SESSION_KEY = "cg.card.ws.sessionKey";
    private static final String ATTR_HELLO_DONE = "cg.card.ws.helloDone";
    private static final String ATTR_VAR_PACKET_SEQ = "cg.card.ws.varPacketSeq";

    private final CardWsSessionRegistry sessionRegistry;
    private final CardWsHandler cardWsHandler;
    private final ObjectMapper objectMapper;
    private final AppVariableMapper appVariableMapper;
    private final LicenseKeyMapper licenseKeyMapper;
    private final AppVariableTemplateResolver appVariableTemplateResolver;

    @Scheduled(fixedRate = INTERVAL_MS)
    public void sendHeartbeat() {
        for (WebSocketSession session : sessionRegistry.all()) {
            sendHeartbeatOnce(session);
        }
    }

    /**
     * 向单个会话发送加密 HEARTBEAT（含变量）
     * 外层: ECDH sessionKey + AES-256-GCM
     * 内层: 时间戳派生 AES-128 密钥加密变量（客户端暴力破解 31 次）
     */
    public void sendHeartbeatOnce(WebSocketSession session) {
        try {
            if (session == null || !session.isOpen()) {
                return;
            }
            Object cardIdObj = session.getAttributes().get(ATTR_CARD_ID);
            if (!(cardIdObj instanceof Long cardId)) {
                return;
            }

            // 未完成 HELLO 握手的会话不发送加密心跳
            if (!Boolean.TRUE.equals(session.getAttributes().get(ATTR_HELLO_DONE))) {
                return;
            }

            Object keyObj = session.getAttributes().get(ATTR_SESSION_KEY);
            if (!(keyObj instanceof byte[] sessionKey)) {
                return;
            }

            // 更新 Redis 心跳时间
            cardWsHandler.updateRedisHeartbeat(cardId);

            long now = Instant.now().toEpochMilli();
            String connId = (String) session.getAttributes().get(ATTR_CONN_ID);

            // 派生子密钥
            long lastPacket = session.getAttributes().get(ATTR_VAR_PACKET_SEQ) instanceof Long l ? l : 0L;
            long varPacketSeq = lastPacket + 1;
            session.getAttributes().put(ATTR_VAR_PACKET_SEQ, varPacketSeq);
            byte[] subKey = WsCrypto.deriveWsVariablePacketSubKey(sessionKey, varPacketSeq);

            // 查询变量
            Long appId = session.getAttributes().get(ATTR_APP_ID) instanceof Long a ? a : null;
            Map<String, Map<String, Object>> byTier = variablesByTier(appId, cardId, connId, now);

            // 内层加密：时间戳派生 AES-128 密钥加密变量
            String variablesJson = objectMapper.writeValueAsString(byTier);
            LocalDateTime serverTime = LocalDateTime.now(ZoneId.systemDefault());
            String tsKey = serverTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String encryptedVars = aesEncryptHex(tsKey, variablesJson);

            // 组装明文 payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("v", 1);
            payload.put("ts", now);
            payload.put("varPacketSeq", varPacketSeq);
            payload.put("variables", encryptedVars);

            // 外层 AES-256-GCM 加密
            byte[] plain = objectMapper.writeValueAsBytes(payload);
            byte[] aad = WsCrypto.utf8(connId + "|" + varPacketSeq + "|" + now);
            WsCrypto.AesGcmPack enc = WsCrypto.aesGcmEncrypt(subKey, plain, aad);

            WsCipher cipher = new WsCipher();
            cipher.setAlg("AES-256-GCM");
            cipher.setIv(WsCrypto.b64(enc.iv()));
            cipher.setData(WsCrypto.b64(enc.ciphertext()));
            cipher.setTag(WsCrypto.b64(enc.tag()));

            WsEnvelope env = new WsEnvelope();
            env.setType("HEARTBEAT");
            env.setConnId(connId);
            env.setTs(now);
            env.setVarPacketSeq(varPacketSeq);
            env.setCipher(cipher);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(env)));
        } catch (Exception e) {
            log.debug("卡密 WS 心跳发送失败: {}", e.getMessage());
        }
    }

    private Map<String, Map<String, Object>> variablesByTier(Long appId, Long cardId, String connId, long nowEpochMs) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.STANDARD), new LinkedHashMap<>());
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.SENSITIVE), new LinkedHashMap<>());
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.CRITICAL), new LinkedHashMap<>());

        if (appId == null) {
            return out;
        }

        AppVariableTemplateContext ctx = new AppVariableTemplateContext();
        ctx.setAppId(appId);
        ctx.setWsConnId(connId);
        ctx.setWsConnectedAtEpochMs(nowEpochMs);

        LicenseKey key = licenseKeyMapper.selectById(cardId);
        if (key != null) {
            ctx.setDeviceId(key.getBindDeviceId());
        }

        List<AppVariable> vars = appVariableMapper.selectList(new LambdaQueryWrapper<AppVariable>()
                .eq(AppVariable::getAppId, appId)
                .eq(AppVariable::getEnabled, true)
                .eq(AppVariable::getDeleted, 0));

        for (AppVariable v : vars) {
            int tier = VariableSecurityTier.normalize(v.getSecurityTier());
            String bucket = VariableSecurityTier.bucketName(tier);
            String resolved = appVariableTemplateResolver.resolve(v.getVariableValue(), ctx);
            out.get(bucket).put(v.getVariableName(), convertVariableValue(resolved, v.getVariableType()));
        }
        return out;
    }

    private Object convertVariableValue(String value, String type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return switch (type == null ? "" : type) {
                case "NUMBER" -> Double.parseDouble(value);
                case "BOOLEAN" -> Boolean.parseBoolean(value);
                case "JSON" -> objectMapper.readTree(value);
                case "ARRAY" -> objectMapper.readValue(value, List.class);
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 时间戳派生 AES-128 加密（ECB 模式，无 IV）。
     * key 格式: yyyyMMddHHmmss (14 字节，补 2 字节 \0 到 16 字节)
     * 客户端暴力破解: 用当前时间 ±15s 生成 31 个 key 尝试解密。
     */
    private String aesEncryptHex(String tsKey, String plaintext) {
        try {
            byte[] keyBytes = new byte[16];
            byte[] src = tsKey.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 16));

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : encrypted) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("变量内层加密失败: {}", e.getMessage());
            return plaintext;
        }
    }
}
