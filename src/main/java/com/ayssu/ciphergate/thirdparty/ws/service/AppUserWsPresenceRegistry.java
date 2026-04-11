package com.ayssu.ciphergate.thirdparty.ws.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端用户 WebSocket 会话在线状态（内存，单机）。用于管理端展示「是否在线 / 会话数 / 在线时长」。
 */
@Service
public class AppUserWsPresenceRegistry {

    public record WsSessionTicket(
            Long appUserId,
            String connId,
            long connectedAtEpochMs,
            String deviceId,
            String clientIp
    ) {}

    private final ConcurrentHashMap<String, WsSessionTicket> byConnId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WsSessionTicket>> byAppUserId = new ConcurrentHashMap<>();

    public void register(String connId, Long appUserId, String deviceId, String clientIp, long connectedAtEpochMs) {
        if (connId == null || connId.isBlank() || appUserId == null) {
            return;
        }
        String dev = deviceId != null && !deviceId.isBlank() ? deviceId.trim() : "";
        String ip = clientIp != null ? clientIp : "";
        WsSessionTicket t = new WsSessionTicket(appUserId, connId, connectedAtEpochMs, dev, ip);
        byConnId.put(connId, t);
        byAppUserId.computeIfAbsent(appUserId, k -> new ConcurrentHashMap<>()).put(connId, t);
    }

    public void unregister(String connId) {
        if (connId == null || connId.isBlank()) {
            return;
        }
        WsSessionTicket t = byConnId.remove(connId);
        if (t == null) {
            return;
        }
        ConcurrentHashMap<String, WsSessionTicket> userMap = byAppUserId.get(t.appUserId());
        if (userMap != null) {
            userMap.remove(connId);
            if (userMap.isEmpty()) {
                byAppUserId.remove(t.appUserId());
            }
        }
    }

    /**
     * 当前 WS 是否在线、会话数、最早连接时间（用于展示在线时长起点）。
     */
    public PresenceSnapshot snapshot(Long appUserId) {
        if (appUserId == null) {
            return PresenceSnapshot.offline();
        }
        Map<String, WsSessionTicket> m = byAppUserId.get(appUserId);
        if (m == null || m.isEmpty()) {
            return PresenceSnapshot.offline();
        }
        long earliest = Long.MAX_VALUE;
        for (WsSessionTicket t : m.values()) {
            earliest = Math.min(earliest, t.connectedAtEpochMs());
        }
        return new PresenceSnapshot(true, m.size(), earliest, List.copyOf(m.values()));
    }

    public static final class PresenceSnapshot {
        private final boolean online;
        private final int sessionCount;
        private final long earliestConnectedAtEpochMs;
        private final List<WsSessionTicket> sessions;

        private PresenceSnapshot(boolean online, int sessionCount, long earliestConnectedAtEpochMs, List<WsSessionTicket> sessions) {
            this.online = online;
            this.sessionCount = sessionCount;
            this.earliestConnectedAtEpochMs = earliestConnectedAtEpochMs;
            this.sessions = sessions;
        }

        public static PresenceSnapshot offline() {
            return new PresenceSnapshot(false, 0, 0L, Collections.emptyList());
        }

        public boolean isOnline() {
            return online;
        }

        public int getSessionCount() {
            return sessionCount;
        }

        /** 最早一条会话的连接时间；不在线时为 0 */
        public long getEarliestConnectedAtEpochMs() {
            return earliestConnectedAtEpochMs;
        }

        public List<WsSessionTicket> getSessions() {
            return sessions;
        }
    }
}
