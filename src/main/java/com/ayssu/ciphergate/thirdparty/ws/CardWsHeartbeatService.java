package com.ayssu.ciphergate.thirdparty.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 卡密 WebSocket 心跳服务。
 * 每 30 秒向所有已连接的卡密 WS 会话发送 HEARTBEAT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardWsHeartbeatService {

    private static final long INTERVAL_MS = 30_000L;
    private static final String ATTR_CARD_ID = "cg.card.ws.cardId";

    private final CardWsSessionRegistry sessionRegistry;
    private final CardWsHandler cardWsHandler;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = INTERVAL_MS)
    public void sendHeartbeat() {
        for (WebSocketSession session : sessionRegistry.all()) {
            sendHeartbeatOnce(session);
        }
    }

    /**
     * 向单个会话发送 HEARTBEAT
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

            // 更新 Redis 心跳时间
            cardWsHandler.updateRedisHeartbeat(cardId);

            // 发送 HEARTBEAT
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "HEARTBEAT",
                    "ts", System.currentTimeMillis()
            ))));
        } catch (Exception e) {
            log.debug("卡密 WS 心跳发送失败: {}", e.getMessage());
        }
    }
}
