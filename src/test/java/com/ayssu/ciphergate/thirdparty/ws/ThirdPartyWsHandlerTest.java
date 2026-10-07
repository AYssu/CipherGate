package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.service.AppVariableTemplateResolver;
import com.ayssu.ciphergate.thirdparty.ws.crypto.WsCrypto;
import com.ayssu.ciphergate.thirdparty.ws.model.WsAuthPayload;
import com.ayssu.ciphergate.thirdparty.ws.model.WsCipher;
import com.ayssu.ciphergate.thirdparty.ws.model.WsEnvelope;
import com.ayssu.ciphergate.thirdparty.ws.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThirdPartyWsHandlerTest {
    private static final String CONN_ID = "auth-test-connection";
    private static final Long USER_ID = 7L;
    private final ObjectMapper mapper = new ObjectMapper();
    private ThirdPartyWsHandler handler;
    private ThirdPartyWsHeartbeatService heartbeat;
    private ThirdPartyWsSessionRegistry sessions;
    private AppUserWsPresenceRegistry presence;
    private AppUserWsLoginRecorder recorder;
    private WebSocketSession session;
    private Map<String, Object> attributes;
    private Application app;
    private KeyPair serverKeys;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        presence = new AppUserWsPresenceRegistry(redis);
        sessions = new ThirdPartyWsSessionRegistry(presence);
        recorder = mock(AppUserWsLoginRecorder.class);
        AppUserWsAuthService auth = mock(AppUserWsAuthService.class);
        WsNonceService nonce = mock(WsNonceService.class);
        when(nonce.markIfNew(any(), any())).thenReturn(true);
        app = new Application();
        app.setId(1L);
        app.setStatus(1);
        app.setBusinessModel(1);
        app.setAppKey("test-app");
        app.setAppSecret(UUID.randomUUID().toString());
        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setAppId(app.getId());
        user.setUsername("test-user");
        user.setMemberExpiresAt(LocalDateTime.now().plusDays(1));
        when(auth.loginAppUser(any(), any(), any())).thenReturn(user);
        ApplicationMapper apps = mock(ApplicationMapper.class);
        when(apps.selectById(app.getId())).thenReturn(app);
        AppUserMapper users = mock(AppUserMapper.class);
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        AppVariableMapper variables = mock(AppVariableMapper.class);
        when(variables.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        heartbeat = new ThirdPartyWsHeartbeatService(sessions, variables, apps, users,
                mapper, mock(AppVariableTemplateResolver.class));
        handler = new ThirdPartyWsHandler(mapper, auth, nonce, sessions, recorder, presence,
                mock(AppUserWsDeviceBindService.class), new AppUserWsSessionKickService(sessions, presence),
                heartbeat, mock(FunctionRuntimeService.class));
        serverKeys = WsCrypto.generateX25519KeyPair();
        attributes = new HashMap<>();
        attributes.put("cg.ws.connId", CONN_ID);
        attributes.put("cg.ws.app", app);
        attributes.put("cg.ws.authed", false);
        attributes.put("cg.ws.sessionKey", new byte[32]);
        attributes.put("cg.ws.clientPub", "test-client-public-key");
        attributes.put("cg.ws.serverKp", serverKeys);
        attributes.put("cg.ws.serverNonce", "test-server-nonce");
        attributes.put("cg.ws.clientIp", "127.0.0.1");
        session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void heartbeatCannotSeeHalfInitializedLogin() throws Exception {
        doAnswer(call -> {
            heartbeat.sendHeartbeat();
            return null;
        }).when(recorder).recordSuccessfulLogin(any(), any(), any(), any());

        authenticate();

        verify(session, never()).close(any());
        assertTrue(presence.snapshot(USER_ID).isOnline());
        assertEquals(1, sessions.size());
        assertEquals(true, attributes.get("cg.ws.authed"));
    }

    @Test
    void heartbeatCannotOvertakeAuthOk() throws Exception {
        doAnswer(call -> {
            TextMessage message = call.getArgument(0);
            if (message.getPayload().contains("AUTH_OK")) {
                assertEquals(0, sessions.size());
                heartbeat.sendHeartbeat();
            }
            return null;
        }).when(session).sendMessage(any());

        authenticate();

        assertTrue(presence.snapshot(USER_ID).isOnline());
        verify(session, times(2)).sendMessage(any()); // AUTH_OK followed by HEARTBEAT
    }

    @Test
    void closedConnectionDuringLoginCannotLeaveOrphanPresence() throws Exception {
        doAnswer(call -> {
            when(session.isOpen()).thenReturn(false);
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);
            return null;
        }).when(recorder).recordSuccessfulLogin(any(), any(), any(), any());

        authenticate();

        assertOffline();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void transportErrorDuringLoginCannotReactivateClosingConnection() throws Exception {
        doAnswer(call -> {
            // Close handshake may leave isOpen() true until the peer replies.
            handler.handleTransportError(session, new IOException("transport failure"));
            return null;
        }).when(recorder).recordSuccessfulLogin(any(), any(), any(), any());

        authenticate();

        assertOffline();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void failedAuthOkWriteDoesNotLeaveOnlineSession() throws Exception {
        doThrow(new IOException("AUTH_OK write failed")).when(session).sendMessage(any());

        assertThrows(IOException.class, this::authenticate);

        assertOffline();
    }

    private void authenticate() throws Exception {
        WsAuthPayload payload = new WsAuthPayload();
        payload.setAppKey(app.getAppKey());
        payload.setUsername("test-user");
        payload.setPassword(UUID.randomUUID().toString());
        payload.setDeviceId("test-device");
        payload.setDeviceName("test-device-name");
        payload.setDeviceOs("Linux");
        payload.setTs(System.currentTimeMillis());
        payload.setNonce(UUID.randomUUID().toString());
        payload.setSeq(1L);
        String sign = String.join("\n", payload.getAppKey(), CONN_ID,
                (String) attributes.get("cg.ws.clientPub"), WsCrypto.b64(serverKeys.getPublic().getEncoded()),
                (String) attributes.get("cg.ws.serverNonce"), payload.getTs().toString(), payload.getNonce(), "1");
        payload.setAppSig(ThirdPartySignatureVerifier.hmacSha256Hex(app.getAppSecret(), sign));
        WsCrypto.AesGcmPack pack = WsCrypto.aesGcmEncrypt(new byte[32], mapper.writeValueAsBytes(payload), null);
        WsCipher cipher = new WsCipher();
        cipher.setIv(WsCrypto.b64(pack.iv()));
        cipher.setData(WsCrypto.b64(pack.ciphertext()));
        cipher.setTag(WsCrypto.b64(pack.tag()));
        WsEnvelope envelope = new WsEnvelope();
        envelope.setType("AUTH");
        envelope.setCipher(cipher);
        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(envelope)));
    }

    private void assertOffline() {
        assertFalse(presence.snapshot(USER_ID).isOnline());
        assertEquals(0, sessions.size());
        assertEquals(false, attributes.get("cg.ws.authed"));
    }
}
