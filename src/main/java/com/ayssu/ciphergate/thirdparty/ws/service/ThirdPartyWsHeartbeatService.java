package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyWsHeartbeatService {
    private static final long INTERVAL_MS = 5_000L;

    // MUST match handler attribute keys
    private static final String ATTR_CONN_ID = "cg.ws.connId";
    private static final String ATTR_APP = "cg.ws.app";
    private static final String ATTR_SESSION_KEY = "cg.ws.sessionKey";
    private static final String ATTR_AUTHED = "cg.ws.authed";

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

                Map<String, Object> variables = getAppVariables(app.getId());
                Map<String, Object> payload = new HashMap<>();
                payload.put("ts", now);
                payload.put("variables", variables);

                byte[] plain = objectMapper.writeValueAsBytes(payload);
                WsCrypto.AesGcmPack enc = WsCrypto.aesGcmEncrypt(sessionKey, plain, null);

                WsCipher cipher = new WsCipher();
                cipher.setAlg("AES-256-GCM");
                cipher.setIv(WsCrypto.b64(enc.iv()));
                cipher.setData(WsCrypto.b64(enc.ciphertext()));
                cipher.setTag(WsCrypto.b64(enc.tag()));

                WsEnvelope env = new WsEnvelope();
                env.setType("HEARTBEAT");
                env.setConnId(connId);
                env.setTs(now);
                env.setCipher(cipher);

                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(env)));
            } catch (Exception e) {
                log.debug("heartbeat send failed: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> getAppVariables(Long appId) {
        List<AppVariable> vars = appVariableMapper.selectList(new LambdaQueryWrapper<AppVariable>()
                .eq(AppVariable::getAppId, appId)
                .eq(AppVariable::getEnabled, true)
                .eq(AppVariable::getDeleted, 0));
        Map<String, Object> out = new HashMap<>();
        for (AppVariable v : vars) {
            out.put(v.getVariableName(), v.getVariableValue());
        }
        return out;
    }
}

