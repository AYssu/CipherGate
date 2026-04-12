package com.ayssu.ciphergate.thirdparty.ws.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThirdPartyWsSessionRegistry {
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
        byId.remove(connId);
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

