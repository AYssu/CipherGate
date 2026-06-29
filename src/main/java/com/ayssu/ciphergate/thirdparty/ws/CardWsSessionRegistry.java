package com.ayssu.ciphergate.thirdparty.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡密 WebSocket 会话注册表。
 * 按 connId 管理在线连接。
 */
@Slf4j
@Component
public class CardWsSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void add(String connId, WebSocketSession session) {
        sessions.put(connId, session);
        log.debug("卡密 WS 会话注册: connId={}", connId);
    }

    public void remove(String connId) {
        sessions.remove(connId);
        log.debug("卡密 WS 会话移除: connId={}", connId);
    }

    public WebSocketSession get(String connId) {
        return sessions.get(connId);
    }

    public Collection<WebSocketSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }
}
