package com.ayssu.ciphergate.thirdparty.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端用户 WebSocket 在线状态（内存）+ 会话片段持久化（Redis）。
 * 1) 管理端在线状态：以内存会话为准，断线立即离线；
 * 2) 30 秒重连续算：短暂掉线后恢复时，连续在线时长不从 0 开始；
 * 3) 会话片段落 Redis（1 天 TTL）：供当天在线时长统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserWsPresenceRegistry {

    public record WsSessionTicket(
            Long appUserId,
            String connId,
            long connectedAtEpochMs,
            long resumedCarrySeconds,
            String deviceId,
            String clientIp
    ) {}

    private static final Duration RECONNECT_GRACE_TTL = Duration.ofSeconds(30);
    private static final Duration REDIS_FRAGMENT_TTL = Duration.ofDays(1);
    private static final String RECONNECT_CARRY_PREFIX = "cg:ws:presence:carry:";
    private static final String DAILY_TOTAL_PREFIX = "cg:ws:presence:daily:sec:";
    private static final String DAILY_FRAGMENT_PREFIX = "cg:ws:presence:daily:frag:";
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, WsSessionTicket> byConnId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WsSessionTicket>> byAppUserId = new ConcurrentHashMap<>();

    public WsSessionTicket register(String connId, Long appUserId, String deviceId, String clientIp, long connectedAtEpochMs) {
        if (connId == null || connId.isBlank() || appUserId == null) {
            return null;
        }
        long now = Math.max(System.currentTimeMillis(), connectedAtEpochMs);
        long carrySeconds = loadReconnectCarrySeconds(appUserId);
        String dev = deviceId != null && !deviceId.isBlank() ? deviceId.trim() : "";
        String ip = clientIp != null ? clientIp : "";
        WsSessionTicket t = new WsSessionTicket(appUserId, connId, now, carrySeconds, dev, ip);
        byConnId.put(connId, t);
        byAppUserId.computeIfAbsent(appUserId, k -> new ConcurrentHashMap<>()).put(connId, t);
        return t;
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
        boolean offlineAfterRemoval = true;
        if (userMap != null) {
            userMap.remove(connId);
            if (userMap.isEmpty()) {
                byAppUserId.remove(t.appUserId());
            } else {
                offlineAfterRemoval = false;
            }
        }
        persistSessionFragment(t, offlineAfterRemoval);
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

    /**
     * 当前在线的终端用户 ID 列表（内存，单机）。
     * 用于管理端列表筛选 wsOnline=true/false。
     */
    public List<Long> listOnlineAppUserIds() {
        if (byAppUserId.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(byAppUserId.keySet());
    }

    /** 当前连续在线时长（秒）：支持 30 秒内重连续算。 */
    public long continuousOnlineSeconds(PresenceSnapshot snapshot, long nowEpochMs) {
        if (snapshot == null || !snapshot.isOnline() || snapshot.getSessions().isEmpty()) {
            return 0L;
        }
        long best = 0L;
        for (WsSessionTicket t : snapshot.getSessions()) {
            long elapsed = Math.max(0L, (nowEpochMs - t.connectedAtEpochMs()) / 1000L);
            best = Math.max(best, t.resumedCarrySeconds() + elapsed);
        }
        return best;
    }

    /** 当天在线时长（秒）：已落库片段 + 当前在线片段（仅当天部分）。 */
    public long todayOnlineSeconds(Long appUserId, PresenceSnapshot snapshot, long nowEpochMs) {
        if (appUserId == null) {
            return 0L;
        }
        long total = readLongValue(dailyTotalKey(todayKey(), appUserId));
        if (snapshot == null || !snapshot.isOnline() || snapshot.getSessions().isEmpty()) {
            return total;
        }
        long dayStartMs = todayStartEpochMs(nowEpochMs);
        long ongoing = 0L;
        for (WsSessionTicket t : snapshot.getSessions()) {
            long start = Math.max(t.connectedAtEpochMs(), dayStartMs);
            long sec = Math.max(0L, (nowEpochMs - start) / 1000L);
            ongoing = Math.max(ongoing, sec);
        }
        return total + ongoing;
    }

    private void persistSessionFragment(WsSessionTicket t, boolean allowCarry) {
        if (t == null || t.appUserId() == null) {
            return;
        }
        long endMs = System.currentTimeMillis();
        long startMs = Math.min(t.connectedAtEpochMs(), endMs);
        if (endMs <= startMs) {
            if (allowCarry) {
                saveReconnectCarrySeconds(t.appUserId(), t.resumedCarrySeconds());
            }
            return;
        }
        persistByDays(t, startMs, endMs);
        long sessionSec = Math.max(0L, (endMs - startMs) / 1000L);
        if (allowCarry) {
            saveReconnectCarrySeconds(t.appUserId(), t.resumedCarrySeconds() + sessionSec);
        }
    }

    private void persistByDays(WsSessionTicket t, long startMs, long endMs) {
        LocalDate startDay = Instant.ofEpochMilli(startMs).atZone(ZONE).toLocalDate();
        LocalDate endDay = Instant.ofEpochMilli(Math.max(startMs, endMs - 1)).atZone(ZONE).toLocalDate();
        LocalDate day = startDay;
        while (!day.isAfter(endDay)) {
            long dayStart = day.atStartOfDay(ZONE).toInstant().toEpochMilli();
            long nextDayStart = day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
            long segStart = Math.max(startMs, dayStart);
            long segEnd = Math.min(endMs, nextDayStart);
            if (segEnd > segStart) {
                long sec = Math.max(0L, (segEnd - segStart) / 1000L);
                if (sec > 0L) {
                    String dayKey = day.format(DAY_FMT);
                    String totalKey = dailyTotalKey(dayKey, t.appUserId());
                    String fragKey = dailyFragmentKey(dayKey, t.appUserId());
                    try {
                        redisTemplate.opsForValue().increment(totalKey, sec);
                        redisTemplate.expire(totalKey, REDIS_FRAGMENT_TTL);
                        String frag = t.connId() + "|" + segStart + "|" + segEnd + "|" + sec + "|" + safe(t.deviceId()) + "|" + safe(t.clientIp());
                        redisTemplate.opsForList().rightPush(fragKey, frag);
                        redisTemplate.expire(fragKey, REDIS_FRAGMENT_TTL);
                    } catch (Exception e) {
                        log.warn("persist ws fragment failed appUserId={} day={} err={}", t.appUserId(), dayKey, e.getMessage());
                    }
                }
            }
            day = day.plusDays(1);
        }
    }

    private long loadReconnectCarrySeconds(Long appUserId) {
        String key = reconnectCarryKey(appUserId);
        long carry = readLongValue(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
        return carry;
    }

    private void saveReconnectCarrySeconds(Long appUserId, long seconds) {
        if (appUserId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(reconnectCarryKey(appUserId), String.valueOf(Math.max(0L, seconds)), RECONNECT_GRACE_TTL);
        } catch (Exception e) {
            log.warn("save reconnect carry failed appUserId={} err={}", appUserId, e.getMessage());
        }
    }

    private long readLongValue(String key) {
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v == null || v.isBlank()) {
                return 0L;
            }
            return Long.parseLong(v.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String reconnectCarryKey(Long appUserId) {
        return RECONNECT_CARRY_PREFIX + appUserId;
    }

    private static String dailyTotalKey(String day, Long appUserId) {
        return DAILY_TOTAL_PREFIX + day + ":" + appUserId;
    }

    private static String dailyFragmentKey(String day, Long appUserId) {
        return DAILY_FRAGMENT_PREFIX + day + ":" + appUserId;
    }

    private static String todayKey() {
        return LocalDate.now(ZONE).format(DAY_FMT);
    }

    private static long todayStartEpochMs(long nowEpochMs) {
        LocalDate d = Instant.ofEpochMilli(nowEpochMs).atZone(ZONE).toLocalDate();
        return d.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("|", "_");
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
