package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.ws.crypto.WsCrypto;
import com.ayssu.ciphergate.thirdparty.ws.model.WsCipher;
import com.ayssu.ciphergate.thirdparty.ws.model.WsEnvelope;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ayssu.ciphergate.thirdparty.ws.service.WsNonceService;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 卡密 WebSocket 处理器。
 * <p>
 * 流程：
 * 1) 客户端通过 HTTP 登录获得心跳 token
 * 2) 连接 ws:///api/v1/card/ws?token=xxx
 * 3) 服务端验证 token，注册会话，发送 CONNECTED
 * 4) 客户端发送 HELLO（携带 clientPubKey），服务端回复 HELLO_ACK（携带 serverPubKey）
 * 5) 服务端定时发送加密 HEARTBEAT（含变量），客户端回复加密 PONG
 * 6) 断开连接 → 标记卡密离线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardWsHandler extends TextWebSocketHandler {

    private static final long MAX_SKEW_MS = 15_000L;
    private static final byte[] HKDF_INFO = WsCrypto.utf8("cg-card-ws-v1");

    private static final String HB_CARD_PREFIX = "cg:hb:card:";
    private static final String HB_TOKEN_PREFIX = "cg:hb:token:";
    private static final String ATTR_CARD_ID = "cg.card.ws.cardId";
    private static final String ATTR_APP_ID = "cg.card.ws.appId";
    private static final String ATTR_CONN_ID = "cg.card.ws.connId";
    private static final String ATTR_TOKEN = "cg.card.ws.token";
    private static final String ATTR_SESSION_KEY = "cg.card.ws.sessionKey";
    private static final String ATTR_SERVER_KP = "cg.card.ws.serverKp";
    private static final String ATTR_CLIENT_PUB = "cg.card.ws.clientPub";
    private static final String ATTR_SERVER_NONCE = "cg.card.ws.serverNonce";
    private static final String ATTR_HELLO_DONE = "cg.card.ws.helloDone";
    private static final String ATTR_LAST_SEQ = "cg.card.ws.lastSeq";
    private static final String ATTR_VAR_PACKET_SEQ = "cg.card.ws.varPacketSeq";

    private final StringRedisTemplate redisTemplate;
    private final LicenseKeyMapper licenseKeyMapper;
    private final CardWsSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final WsNonceService wsNonceService;
    private final FunctionRuntimeService functionRuntimeService;

    /**
     * 连接建立：从 query param 取 token 验证
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        if (!StringUtils.hasText(token)) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "MISSING_TOKEN");
            return;
        }

        // 验证 token → cardId
        String cardIdStr = redisTemplate.opsForValue().get(HB_TOKEN_PREFIX + token);
        if (cardIdStr == null) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "TOKEN_INVALID");
            return;
        }

        Long cardId = Long.parseLong(cardIdStr);
        String cardKey = HB_CARD_PREFIX + cardId;
        String json = redisTemplate.opsForValue().get(cardKey);
        if (json == null) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "TOKEN_EXPIRED");
            return;
        }

        JSONObject payload = JSON.parseObject(json);
        if (!token.equals(payload.getString("token"))) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "TOKEN_STALE");
            return;
        }

        Long appId = payload.getLong("appId");
        String connId = UUID.randomUUID().toString().replace("-", "");

        session.getAttributes().put(ATTR_CARD_ID, cardId);
        session.getAttributes().put(ATTR_APP_ID, appId);
        session.getAttributes().put(ATTR_CONN_ID, connId);
        session.getAttributes().put(ATTR_TOKEN, token);
        session.getAttributes().put(ATTR_HELLO_DONE, false);
        session.getAttributes().put(ATTR_LAST_SEQ, 0L);
        session.getAttributes().put(ATTR_VAR_PACKET_SEQ, 0L);

        sessionRegistry.add(connId, session);

        // 标记 DB 在线
        markOnline(cardId);

        // 发送连接成功消息
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "CONNECTED",
                "connId", connId,
                "cardId", cardId,
                "ts", System.currentTimeMillis()
        ))));

        log.info("卡密 WS 连接建立: cardId={}, connId={}", cardId, connId);
    }

    /**
     * 连接关闭：清理会话，标记离线
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String connId = (String) session.getAttributes().get(ATTR_CONN_ID);
        Object cardIdObj = session.getAttributes().get(ATTR_CARD_ID);

        if (connId != null) {
            sessionRegistry.remove(connId);
        }

        if (cardIdObj instanceof Long cardId) {
            markOffline(cardId);
            log.info("卡密 WS 连接关闭: cardId={}, connId={}, status={}", cardId, connId, status);
        }

        super.afterConnectionClosed(session, status);
    }

    /**
     * 处理客户端消息：HELLO / PONG
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsEnvelope env;
        try {
            env = objectMapper.readValue(message.getPayload(), WsEnvelope.class);
        } catch (Exception e) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "BAD_JSON");
            return;
        }

        String type = env.getType();
        if (!StringUtils.hasText(type)) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "MISSING_TYPE");
            return;
        }

        switch (type) {
            case "HELLO" -> handleHello(session, env);
            case "PONG" -> handlePong(session, env);
            case "FUNC_CALL" -> handleFuncCall(session, env);
            default -> close(session, CloseStatus.NOT_ACCEPTABLE, "UNSUPPORTED");
        }
    }

    /**
     * 处理 HELLO：客户端发送 x25519 公钥，服务端完成密钥协商
     */
    private void handleHello(WebSocketSession session, WsEnvelope env) throws Exception {
        if (Boolean.TRUE.equals(session.getAttributes().get(ATTR_HELLO_DONE))) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "ALREADY_HELLO");
            return;
        }

        long now = Instant.now().toEpochMilli();
        Long ts = env.getTs();
        if (ts == null || Math.abs(now - ts) > MAX_SKEW_MS) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "EXPIRED");
            return;
        }
        if (!wsNonceService.markIfNew("card:hello", env.getNonce())) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "REPLAY");
            return;
        }
        if (!StringUtils.hasText(env.getClientPubKey())) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "MISSING_PUBKEY");
            return;
        }

        byte[] clientPubBytes;
        try {
            clientPubBytes = WsCrypto.b64d(env.getClientPubKey());
        } catch (Exception e) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "BAD_PUBKEY");
            return;
        }
        PublicKey clientPub = WsCrypto.decodeX25519PublicKey(clientPubBytes);
        KeyPair serverKp = WsCrypto.generateX25519KeyPair();

        byte[] shared = WsCrypto.ecdhX25519(serverKp, clientPub);
        byte[] serverNonce = randomNonceB64();
        byte[] salt = WsCrypto.hmacSha256(WsCrypto.utf8(env.getNonce()), serverNonce);
        byte[] sessionKey = WsCrypto.hkdfSha256(shared, salt, HKDF_INFO, 32);

        session.getAttributes().put(ATTR_SESSION_KEY, sessionKey);
        session.getAttributes().put(ATTR_SERVER_KP, serverKp);
        session.getAttributes().put(ATTR_CLIENT_PUB, env.getClientPubKey());
        session.getAttributes().put(ATTR_SERVER_NONCE, WsCrypto.b64(serverNonce));
        session.getAttributes().put(ATTR_HELLO_DONE, true);

        WsEnvelope ack = new WsEnvelope();
        ack.setType("HELLO_ACK");
        ack.setConnId((String) session.getAttributes().get(ATTR_CONN_ID));
        ack.setTs(now);
        ack.setServerPubKey(WsCrypto.b64(serverKp.getPublic().getEncoded()));
        ack.setServerNonce((String) session.getAttributes().get(ATTR_SERVER_NONCE));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));

        log.debug("卡密 WS HELLO 完成: connId={}", session.getAttributes().get(ATTR_CONN_ID));
    }

    /**
     * 处理加密 PONG：客户端用 sessionKey 加密回复，更新 Redis 心跳时间
     */
    private void handlePong(WebSocketSession session, WsEnvelope env) throws Exception {
        Object cardIdObj = session.getAttributes().get(ATTR_CARD_ID);
        if (!(cardIdObj instanceof Long cardId)) {
            return;
        }

        byte[] sessionKey = (byte[]) session.getAttributes().get(ATTR_SESSION_KEY);
        if (sessionKey == null) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "NO_SESSION_KEY");
            return;
        }

        // 校验 seq 单调递增
        long lastSeq = session.getAttributes().get(ATTR_LAST_SEQ) instanceof Long l ? l : 0L;
        long seq = env.getSeq() == null ? 0L : env.getSeq();
        if (seq <= lastSeq) {
            close(session, CloseStatus.NOT_ACCEPTABLE, "BAD_SEQ");
            return;
        }
        session.getAttributes().put(ATTR_LAST_SEQ, seq);

        // 解密 PONG payload（可选：客户端可携带自定义数据）
        if (env.getCipher() != null && StringUtils.hasText(env.getCipher().getData())) {
            try {
                WsCipher c = env.getCipher();
                byte[] iv = WsCrypto.b64d(c.getIv());
                byte[] ct = WsCrypto.b64d(c.getData());
                byte[] tag = StringUtils.hasText(c.getTag()) ? WsCrypto.b64d(c.getTag()) : new byte[0];
                String connId = (String) session.getAttributes().get(ATTR_CONN_ID);
                byte[] aad = WsCrypto.utf8(connId + "|" + seq + "|" + env.getTs());
                WsCrypto.aesGcmDecrypt(sessionKey, iv, ct, tag, aad);
                // 解密成功即验证通过
            } catch (Exception e) {
                close(session, CloseStatus.NOT_ACCEPTABLE, "DECRYPT_FAIL");
                return;
            }
        }

        updateRedisHeartbeat(cardId);
    }

    /**
     * 处理 FUNC_CALL：客户端调用插件函数
     */
    private void handleFuncCall(WebSocketSession session, WsEnvelope env) throws Exception {
        // 校验参数
        String funcName = env.getFunc();
        if (!StringUtils.hasText(funcName)) {
            sendFuncError(session, env.getReqId(), null, "MISSING_FUNC", "缺少函数名称");
            return;
        }

        // 获取卡密ID作为 pluginId
        Object cardIdObj = session.getAttributes().get(ATTR_CARD_ID);
        Object appIdObj = session.getAttributes().get(ATTR_APP_ID);
        if (!(cardIdObj instanceof Long cardId)) {
            sendFuncError(session, env.getReqId(), funcName, "NO_CARD", "无法获取卡密信息");
            return;
        }

        // 使用 appId 作为 pluginId（如果有的话），否则用 cardId
        String pluginId = appIdObj instanceof Long appId ? String.valueOf(appId) : String.valueOf(cardId);

        Map<String, Object> params = env.getParams();
        if (params == null) {
            params = Map.of();
        }

        // 执行函数
        FunctionResult result = functionRuntimeService.executeFunction(pluginId, funcName, params);

        // 发送响应
        if (result.success()) {
            WsEnvelope resp = new WsEnvelope();
            resp.setType("FUNC_RESULT");
            resp.setReqId(env.getReqId());
            resp.setFunc(funcName);
            resp.setData(result.data());
            resp.setTs(Instant.now().toEpochMilli());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        } else {
            sendFuncError(session, env.getReqId(), funcName, result.code(), result.message());
        }
    }

    private void sendFuncError(WebSocketSession session, String reqId, String func, String code, String message) throws Exception {
        WsEnvelope resp = new WsEnvelope();
        resp.setType("FUNC_ERROR");
        resp.setReqId(reqId);
        resp.setFunc(func);
        resp.setCode(code);
        resp.setMessage(message);
        resp.setTs(Instant.now().toEpochMilli());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
    }

    /**
     * 传输错误处理
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("卡密 WS 传输错误: {}", exception.getMessage());
        super.handleTransportError(session, exception);
    }

    /**
     * 更新 Redis 中的心跳时间
     */
    public void updateRedisHeartbeat(Long cardId) {
        String cardKey = HB_CARD_PREFIX + cardId;
        String json = redisTemplate.opsForValue().get(cardKey);
        if (json != null) {
            try {
                JSONObject payload = JSON.parseObject(json);
                payload.put("lastHeartbeatAt", LocalDateTime.now().toString());
                // 保持原 TTL
                Long ttl = redisTemplate.getExpire(cardKey);
                if (ttl != null && ttl > 0) {
                    redisTemplate.opsForValue().set(cardKey, payload.toJSONString(), ttl, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.warn("更新 Redis 心跳失败: cardId={}", cardId, e);
            }
        }
    }

    /**
     * 标记卡密在线
     */
    private void markOnline(Long cardId) {
        try {
            LicenseKey key = licenseKeyMapper.selectById(cardId);
            if (key != null && !Boolean.TRUE.equals(key.getIsOnline())) {
                key.setIsOnline(true);
                key.setUpdatedAt(LocalDateTime.now());
                licenseKeyMapper.updateById(key);
            }
        } catch (Exception e) {
            log.warn("标记卡密在线失败: cardId={}", cardId, e);
        }
    }

    /**
     * 标记卡密离线 + 清理 Redis
     */
    private void markOffline(Long cardId) {
        try {
            LicenseKey key = licenseKeyMapper.selectById(cardId);
            if (key != null && Boolean.TRUE.equals(key.getIsOnline())) {
                key.setIsOnline(false);
                key.setUpdatedAt(LocalDateTime.now());
                licenseKeyMapper.updateById(key);
            }
        } catch (Exception e) {
            log.warn("标记卡密离线失败: cardId={}", cardId, e);
        }

        // 清理 Redis 心跳数据
        try {
            String cardKey = HB_CARD_PREFIX + cardId;
            String json = redisTemplate.opsForValue().get(cardKey);
            if (json != null) {
                JSONObject payload = JSON.parseObject(json);
                String token = payload.getString("token");
                if (token != null) {
                    redisTemplate.delete(HB_TOKEN_PREFIX + token);
                }
            }
            redisTemplate.delete(cardKey);
        } catch (Exception e) {
            log.warn("清理 Redis 心跳数据失败: cardId={}", cardId, e);
        }
    }

    /**
     * 从 WebSocket URI 中提取 token 参数
     */
    private String extractToken(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "token".equals(kv[0])) {
                        return kv[1];
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] randomNonceB64() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    private void close(WebSocketSession session, CloseStatus status, String reason) {
        try {
            log.debug("卡密 WS 关闭: reason={}", reason);
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}
