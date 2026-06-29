package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyHeartbeatService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡密 WebSocket 处理器。
 * <p>
 * 流程：
 * 1) 客户端通过 HTTP 登录获得心跳 token
 * 2) 连接 ws:///api/v1/card/ws?token=xxx
 * 3) 服务端验证 token，注册会话
 * 4) 服务端定时发送 HEARTBEAT，客户端回复 PONG
 * 5) 断开连接 → 标记卡密离线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardWsHandler extends TextWebSocketHandler {

    private static final String HB_CARD_PREFIX = "cg:hb:card:";
    private static final String HB_TOKEN_PREFIX = "cg:hb:token:";
    private static final String ATTR_CARD_ID = "cg.card.ws.cardId";
    private static final String ATTR_APP_ID = "cg.card.ws.appId";
    private static final String ATTR_CONN_ID = "cg.card.ws.connId";
    private static final String ATTR_TOKEN = "cg.card.ws.token";

    private final StringRedisTemplate redisTemplate;
    private final LicenseKeyMapper licenseKeyMapper;
    private final CardWsSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

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
     * 处理客户端消息：PONG
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 客户端回复 PONG，更新 Redis 心跳时间
        if (payload.contains("\"PONG\"") || payload.contains("\"pong\"")) {
            Object cardIdObj = session.getAttributes().get(ATTR_CARD_ID);
            if (cardIdObj instanceof Long cardId) {
                updateRedisHeartbeat(cardId);
            }
        }
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

    private void close(WebSocketSession session, CloseStatus status, String reason) {
        try {
            log.debug("卡密 WS 关闭: reason={}", reason);
            session.close(status);
        } catch (Exception ignored) {
        }
    }
}
