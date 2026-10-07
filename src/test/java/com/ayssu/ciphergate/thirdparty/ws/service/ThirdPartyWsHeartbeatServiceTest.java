package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.thirdparty.service.AppVariableTemplateResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThirdPartyWsHeartbeatServiceTest {
    private static final String CONN_ID = "test-connection";
    private static final Long USER_ID = 7L;

    private ThirdPartyWsSessionRegistry sessions;
    private AppUserWsPresenceRegistry presence;
    private ThirdPartyWsHeartbeatService heartbeat;
    private Application app;
    private AppUser user;
    private AppUserMapper users;
    private ApplicationMapper apps;
    private WebSocketSession session;
    private Map<String, Object> attributes;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.opsForList()).thenReturn(mock(ListOperations.class));
        presence = new AppUserWsPresenceRegistry(redis);
        sessions = new ThirdPartyWsSessionRegistry(presence);
        users = mock(AppUserMapper.class);
        apps = mock(ApplicationMapper.class);
        AppVariableMapper variables = mock(AppVariableMapper.class);
        when(variables.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        heartbeat = new ThirdPartyWsHeartbeatService(sessions, variables, apps, users,
                new ObjectMapper(), mock(AppVariableTemplateResolver.class));

        app = new Application();
        app.setId(1L);
        app.setStatus(1);
        app.setBusinessModel(1);
        when(apps.selectById(app.getId())).thenReturn(app);
        user = new AppUser();
        user.setId(USER_ID);
        user.setAppId(app.getId());
        user.setMemberExpiresAt(LocalDateTime.now().plusDays(1));
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        attributes = new HashMap<>();
        attributes.put("cg.ws.connId", CONN_ID);
        attributes.put("cg.ws.authed", true);
        attributes.put("cg.ws.app", app);
        attributes.put("cg.ws.appUserId", USER_ID);
        attributes.put("cg.ws.sessionKey", new byte[32]);
        session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        sessions.add(CONN_ID, session);
        presence.register(CONN_ID, USER_ID, "device", "127.0.0.1", System.currentTimeMillis());
    }

    @Test
    void expiredPaidUserGoesOfflineWithoutWaitingForCloseCallback() throws Exception {
        user.setMemberExpiresAt(LocalDateTime.now().minusSeconds(1));

        heartbeat.sendHeartbeat();

        verify(session).close(new CloseStatus(1008, "MEMBER_EXPIRED"));
        assertOffline();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void expiredUserGoesOfflineEvenIfTransportCloseFails() throws Exception {
        user.setMemberExpiresAt(LocalDateTime.now().minusSeconds(1));
        doThrow(new IOException("transport close failed")).when(session).close(any());

        heartbeat.sendHeartbeat();

        assertOffline();
    }

    @Test
    void alreadyClosedSessionIsSweptWithoutCloseCallback() throws Exception {
        when(session.isOpen()).thenReturn(false);

        heartbeat.sendHeartbeat();

        assertOffline();
        verify(session, never()).sendMessage(any());
        verifyNoInteractions(apps, users);
    }

    @Test
    void heartbeatWriteFailureClearsOnlineState() throws Exception {
        doThrow(new IOException("broken pipe")).when(session).sendMessage(any());

        heartbeat.sendHeartbeat();

        assertOffline();
        verify(session).close(CloseStatus.SERVER_ERROR);
    }

    @Test
    void validPaidUserStaysOnline() throws Exception {
        heartbeat.sendHeartbeat();

        assertTrue(presence.snapshot(USER_ID).isOnline());
        assertEquals(1, sessions.size());
        verify(session).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any());
    }

    @Test
    void freeModeDoesNotRequireActiveMembership() throws Exception {
        app.setBusinessModel(2);
        user.setMemberExpiresAt(LocalDateTime.now().minusDays(1));

        heartbeat.sendHeartbeat();

        assertTrue(presence.snapshot(USER_ID).isOnline());
        verify(session).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any());
    }

    private void assertOffline() {
        assertFalse(presence.snapshot(USER_ID).isOnline());
        assertTrue(presence.listOnlineAppUserIds().isEmpty());
        assertEquals(0, presence.continuousOnlineSeconds(presence.snapshot(USER_ID), System.currentTimeMillis()));
        assertEquals(0, sessions.size());
        assertEquals(false, attributes.get("cg.ws.authed"));
    }
}
