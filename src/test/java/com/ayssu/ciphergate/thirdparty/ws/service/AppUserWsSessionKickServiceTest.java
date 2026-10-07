package com.ayssu.ciphergate.thirdparty.ws.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppUserWsSessionKickServiceTest {
    private static final Long USER_ID = 7L;
    private static final String CONN_ID = "kick-test-connection";
    private AppUserWsPresenceRegistry presence;
    private ThirdPartyWsSessionRegistry sessions;
    private AppUserWsSessionKickService kicker;
    private WebSocketSession session;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        presence = new AppUserWsPresenceRegistry(redis);
        sessions = new ThirdPartyWsSessionRegistry(presence);
        kicker = new AppUserWsSessionKickService(sessions, presence);
        session = register(CONN_ID, "device-1");
    }

    @Test
    void kickClearsPresenceWithoutWaitingForCloseCallback() throws Exception {
        kicker.kickByAppUserId(USER_ID, null, AppUserWsSessionKickService.KICK_MEMBER_EXPIRED);

        assertOffline();
        verify(session).close(AppUserWsSessionKickService.KICK_MEMBER_EXPIRED);
    }

    @Test
    void kickClearsPresenceEvenWhenCloseThrows() throws Exception {
        doThrow(new IOException("close failed")).when(session).close(any());

        kicker.kickByAppUserId(USER_ID, null);

        assertOffline();
    }

    @Test
    void kickClearsOrphanPresenceWithoutTransportSession() {
        sessions.remove(CONN_ID);
        presence.register(CONN_ID, USER_ID, "device-1", "127.0.0.1", System.currentTimeMillis());

        kicker.kickByAppUserId(USER_ID, null);

        assertOffline();
    }

    @Test
    void kickClearsAlreadyClosedTransport() throws Exception {
        when(session.isOpen()).thenReturn(false);

        kicker.kickByAppUserId(USER_ID, null);

        assertOffline();
        verify(session, never()).close(any());
    }

    @Test
    void deviceSpecificKickPreservesOtherSession() throws Exception {
        WebSocketSession other = register("other-connection", "device-2");

        kicker.kickByAppUserId(USER_ID, "device-1");

        var snapshot = presence.snapshot(USER_ID);
        assertTrue(snapshot.isOnline());
        assertEquals(1, snapshot.getSessionCount());
        assertEquals("other-connection", snapshot.getSessions().get(0).connId());
        assertEquals(1, sessions.size());
        verify(other, never()).close(any());
    }

    @Test
    void repeatedRemovalDoesNotPersistOnlineTimeTwice() {
        kicker.kickByAppUserId(USER_ID, null);
        sessions.remove(CONN_ID);
        sessions.close(session, CloseStatus.NORMAL);

        assertOffline();
        verify(values, times(1)).set(eq("cg:ws:presence:carry:" + USER_ID), anyString(), eq(Duration.ofSeconds(30)));
    }

    private WebSocketSession register(String connId, String deviceId) {
        WebSocketSession ws = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("cg.ws.connId", connId);
        attrs.put("cg.ws.authed", true);
        when(ws.getAttributes()).thenReturn(attrs);
        when(ws.isOpen()).thenReturn(true);
        sessions.add(connId, ws);
        presence.register(connId, USER_ID, deviceId, "127.0.0.1", System.currentTimeMillis());
        return ws;
    }

    private void assertOffline() {
        assertFalse(presence.snapshot(USER_ID).isOnline());
        assertEquals(0, sessions.size());
        assertEquals(false, session.getAttributes().get("cg.ws.authed"));
    }
}
