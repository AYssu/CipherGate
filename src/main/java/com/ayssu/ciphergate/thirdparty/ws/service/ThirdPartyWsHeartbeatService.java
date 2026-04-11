package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.VariableSecurityTier;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.thirdparty.ws.ThirdPartyWsHandler;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyWsHeartbeatService {
    private static final long INTERVAL_MS = 5_000L;

    private static final String ATTR_CONN_ID = "cg.ws.connId";
    private static final String ATTR_APP = "cg.ws.app";
    private static final String ATTR_SESSION_KEY = "cg.ws.sessionKey";
    private static final String ATTR_AUTHED = "cg.ws.authed";
    private static final String ATTR_VAR_PACKET_SEQ = ThirdPartyWsHandler.ATTR_VAR_PACKET_SEQ;

    private final ThirdPartyWsSessionRegistry sessionRegistry;
    private final AppVariableMapper appVariableMapper;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = INTERVAL_MS)
    public void sendHeartbeat() {
        long now = Instant.now().toEpochMilli();
        for (WebSocketSession session : sessionRegistry.all()) {
            try {
                if (session == null || !session.isOpen()) {
                    continue;
                }
                Object authedObj = session.getAttributes().get(ATTR_AUTHED);
                if (!(authedObj instanceof Boolean b && b)) {
                    continue;
                }
                Object appObj = session.getAttributes().get(ATTR_APP);
                Object keyObj = session.getAttributes().get(ATTR_SESSION_KEY);
                Object connObj = session.getAttributes().get(ATTR_CONN_ID);
                if (!(appObj instanceof Application app) || !(keyObj instanceof byte[] sessionKey) || !(connObj instanceof String connId)) {
                    continue;
                }

                long lastPacket = session.getAttributes().get(ATTR_VAR_PACKET_SEQ) instanceof Long l ? l : 0L;
                long varPacketSeq = lastPacket + 1;
                session.getAttributes().put(ATTR_VAR_PACKET_SEQ, varPacketSeq);

                byte[] subKey = WsCrypto.deriveWsVariablePacketSubKey(sessionKey, varPacketSeq);

                Map<String, Map<String, Object>> byTier = variablesByTier(app.getId());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("v", 1);
                payload.put("ts", now);
                payload.put("varPacketSeq", varPacketSeq);
                payload.put("variablesByTier", byTier);

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
                log.debug("heartbeat send failed: {}", e.getMessage());
            }
        }
    }

    private Map<String, Map<String, Object>> variablesByTier(Long appId) {
        List<AppVariable> vars = appVariableMapper.selectList(new LambdaQueryWrapper<AppVariable>()
                .eq(AppVariable::getAppId, appId)
                .eq(AppVariable::getEnabled, true)
                .eq(AppVariable::getDeleted, 0));
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.STANDARD), new LinkedHashMap<>());
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.SENSITIVE), new LinkedHashMap<>());
        out.put(VariableSecurityTier.bucketName(VariableSecurityTier.CRITICAL), new LinkedHashMap<>());
        for (AppVariable v : vars) {
            int tier = VariableSecurityTier.normalize(v.getSecurityTier());
            String bucket = VariableSecurityTier.bucketName(tier);
            out.get(bucket).put(v.getVariableName(), convertVariableValue(v.getVariableValue(), v.getVariableType()));
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
}
