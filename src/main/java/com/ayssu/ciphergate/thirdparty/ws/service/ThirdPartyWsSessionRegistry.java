package com.ayssu.ciphergate.thirdparty.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyWsSessionRegistry {
    /** 终止是不可逆的：关闭握手尚未完成时也不允许 AUTH 再发布在线状态。 */
    public static final String ATTR_TERMINATED = "cg.ws.terminated";

    private final AppUserWsPresenceRegistry presenceRegistry;

    private final ConcurrentHashMap<String, WebSocketSession> byId = new ConcurrentHashMap<>();

    public void add(String connId, WebSocketSession session) {
        if (connId == null || connId.isBlank() || session == null) {
            return;
        }
        byId.put(connId, session);
    }

    public void remove(String connId) {
        if (connId == null || connId.isBlank()) {
            return;
        }
        WebSocketSession session = byId.remove(connId);
        if (session != null) {
            synchronized (session.getAttributes()) {
                session.getAttributes().put(ATTR_TERMINATED, true);
                session.getAttributes().put("cg.ws.authed", false);
            }
        }
        // 在线记录必须立即撤销，不能依赖网络关闭握手完成后的回调。
        // unregister 是幂等的，重复收到关闭/错误回调不会重复累计时长。
        presenceRegistry.unregister(connId);
    }

    /** 所有主动断线共用此入口：先下线，再尝试关闭传输。 */
    public void close(WebSocketSession session, CloseStatus status) {
        if (session == null) {
            return;
        }
        Object connId;
        // 与 Handler 的 AUTH 发布共用锁，防止断线清理后又写回 presence。
        synchronized (session.getAttributes()) {
            session.getAttributes().put(ATTR_TERMINATED, true);
            session.getAttributes().put("cg.ws.authed", false);
            connId = session.getAttributes().get("cg.ws.connId");
            if (connId instanceof String id) {
                remove(id);
            }
        }
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (Exception e) {
            log.warn("close ws failed, connId={}, reason={}", connId, status.getReason(), e);
        }
    }

    public WebSocketSession get(String connId) {
        if (connId == null || connId.isBlank()) {
            return null;
        }
        return byId.get(connId);
    }

    public Collection<WebSocketSession> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}

