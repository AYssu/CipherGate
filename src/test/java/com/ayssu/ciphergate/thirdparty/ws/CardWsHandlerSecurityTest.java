package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.service.AppVariableTemplateResolver;
import com.ayssu.ciphergate.thirdparty.ws.crypto.WsCrypto;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ayssu.ciphergate.thirdparty.ws.model.WsCipher;
import com.ayssu.ciphergate.thirdparty.ws.model.WsEnvelope;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.ayssu.ciphergate.thirdparty.ws.service.WsNonceService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CardWsHandlerSecurityTest {
    private static final Long CARD_ID = 7L;
    private static final Long APP_ID = 11L;
    private static final String TOKEN = "synthetic-card-token";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> attributes = new HashMap<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LicenseKeyMapper cards;
    private ApplicationMapper apps;
    private FunctionRuntimeService functions;
    private CardWsSessionRegistry registry;
    private CardWsHandler handler;
    private WebSocketSession session;
    private LicenseKey card;
    private Application app;
    private String currentConnectionId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "card-security-test"), LicenseKey.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("cg:hb:token:" + TOKEN)).thenReturn(CARD_ID.toString());
        when(values.get("cg:hb:card:" + CARD_ID)).thenAnswer(call -> currentPayload());
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenAnswer(call -> {
            RedisScript<?> script = call.getArgument(0);
            if (script.getScriptAsString().contains("p.connectionId = ARGV[2]")) {
                currentConnectionId = call.getArgument(3);
            }
            return 1L;
        });
        cards = mock(LicenseKeyMapper.class);
        apps = mock(ApplicationMapper.class);
        functions = mock(FunctionRuntimeService.class);
        registry = new CardWsSessionRegistry();
        WsNonceService nonce = mock(WsNonceService.class);
        when(nonce.markIfNew(any(), any())).thenReturn(true);
        handler = new CardWsHandler(redis, cards, apps, registry, mapper, nonce, functions);
        card = new LicenseKey();
        card.setId(CARD_ID);
        card.setAppId(APP_ID);
        card.setStatus(2);
        card.setDeleted(0);
        card.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(cards.selectById(CARD_ID)).thenReturn(card);
        app = new Application();
        app.setId(APP_ID);
        app.setStatus(1);
        app.setDeleted(0);
        when(apps.selectById(APP_ID)).thenReturn(app);
        session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/api/v1/card/ws?token=" + TOKEN));
        when(session.isOpen()).thenReturn(true); // Close handshake can leave the socket open temporarily.
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "disabled", "deleted", "redeemed", "foreign-app", "disabled-app", "deleted-app", "stale-token"})
    void changedEntitlementStopsFunctionAndRemovesConnection(String change) throws Exception {
        connectAndHello();
        switch (change) {
            case "expired" -> card.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            case "disabled" -> card.setStatus(4);
            case "deleted" -> card.setDeleted(1);
            case "redeemed" -> card.setRedeemedAt(LocalDateTime.now());
            case "foreign-app" -> card.setAppId(APP_ID + 1);
            case "disabled-app" -> app.setStatus(0);
            case "deleted-app" -> app.setDeleted(1);
            case "stale-token" -> when(values.get("cg:hb:card:" + CARD_ID)).thenReturn("{\"token\":\"replacement-token\"}");
            default -> fail("Unknown case");
        }

        send(functionCall());

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertEquals(0, registry.size());
        verifyNoInteractions(functions);
        // Restoring database state must not revive a socket already being closed.
        card.setStatus(2);
        card.setDeleted(0);
        card.setRedeemedAt(null);
        card.setAppId(APP_ID);
        card.setExpiresAt(LocalDateTime.now().plusDays(1));
        app.setStatus(1);
        app.setDeleted(0);
        when(values.get("cg:hb:card:" + CARD_ID)).thenAnswer(call -> currentPayload());
        send(functionCall());
        verifyNoInteractions(functions);
    }

    @Test
    void validCardNegotiatesHelloAndCallsOnlyItsApplication() throws Exception {
        connectAndHello();
        when(functions.executeFunction(APP_ID, "sample-plugin", "sample-function", Map.of())).thenReturn(FunctionResult.ok(Map.of("ok", true)));

        send(functionCall());

        verify(functions).executeFunction(APP_ID, "sample-plugin", "sample-function", Map.of());
        verify(session, never()).close(any());
        assertEquals(1, registry.size());
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(3)).sendMessage(messages.capture());
        assertEquals("FUNC_RESULT", mapper.readTree(messages.getValue().getPayload()).path("type").asText());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void connectionClaimPreservesHeartbeatExpiryUntilClientPong() throws Exception {
        handler.afterConnectionEstablished(session);
        ArgumentCaptor<RedisScript> claim = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(claim.capture(), eq(List.of("cg:hb:card:" + CARD_ID)), eq(TOKEN), eq(currentConnectionId));
        assertTrue(claim.getValue().getScriptAsString().contains("p.token ~= ARGV[1]"));
        assertTrue(claim.getValue().getScriptAsString().contains("'KEEPTTL'"));
        assertFalse(claim.getValue().getScriptAsString().contains("'EX'"));
    }

    @Test
    void functionBeforeHelloIsRejected() throws Exception {
        handler.afterConnectionEstablished(session);
        send(functionCall());
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verifyNoInteractions(functions);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void oldConnectionCloseDeletesOnlyExpectedTokenAndScopesDatabaseUpdateByConnection() throws Exception {
        connectAndHello();
        String oldConnId = (String) attributes.get("cg.card.ws.connId");
        when(values.get("cg:hb:card:" + CARD_ID)).thenReturn("{\"token\":\"replacement-token\"}");
        clearInvocations(cards, redis);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of("cg:hb:card:" + CARD_ID, "cg:hb:token:" + TOKEN)), eq(TOKEN), eq(oldConnId));
        assertTrue(script.getValue().getScriptAsString().contains("p.token ~= ARGV[1] or p.connectionId ~= ARGV[2]"));
        assertTrue(script.getValue().getScriptAsString().contains("return 0"));
        verify(redis, never()).delete(anyString());
        verify(redis, never()).delete(anyCollection());
        ArgumentCaptor<Wrapper<LicenseKey>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(cards).update(isNull(), update.capture());
        LambdaUpdateWrapper<LicenseKey> wrapper = (LambdaUpdateWrapper<LicenseKey>) update.getValue();
        assertTrue(wrapper.getSqlSegment().contains("connection_id ="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(oldConnId));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(CARD_ID));
        assertEquals(0, registry.size());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sameTokenReconnectClaimsNewConnectionAndOldSocketCannotRenewOrDeleteIt() throws Exception {
        connectAndHello();
        String oldConnId = currentConnectionId;
        Map<String, Object> newAttributes = new HashMap<>();
        WebSocketSession replacement = mock(WebSocketSession.class);
        when(replacement.getAttributes()).thenReturn(newAttributes);
        URI replacementUri = session.getUri();
        when(replacement.getUri()).thenReturn(replacementUri);
        when(replacement.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(replacement);

        assertNotEquals(oldConnId, currentConnectionId);
        assertEquals(newAttributes.get("cg.card.ws.connId"), currentConnectionId);
        assertFalse(handler.validateEntitlement(session));
        assertTrue(handler.validateEntitlement(replacement));
        clearInvocations(redis);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        ArgumentCaptor<RedisScript> cleanup = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(cleanup.capture(), eq(List.of("cg:hb:card:" + CARD_ID, "cg:hb:token:" + TOKEN)), eq(TOKEN), eq(oldConnId));
        assertTrue(cleanup.getValue().getScriptAsString().contains("p.connectionId ~= ARGV[2]"));
        assertTrue(handler.validateEntitlement(replacement));
        assertEquals(1, registry.size());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void serverHeartbeatDoesNotRenewRedisPresence() throws Exception {
        connectAndHello();
        AppVariableMapper variables = mock(AppVariableMapper.class);
        when(variables.selectList(any(Wrapper.class))).thenReturn(List.of());
        var heartbeat = new CardWsHeartbeatService(registry, handler, mapper, variables, cards, mock(AppVariableTemplateResolver.class));
        clearInvocations(session, redis);

        heartbeat.sendHeartbeatOnce(session);

        verify(session).sendMessage(any(TextMessage.class));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void revokedCardHeartbeatCannotReadOrSendVariables() throws Exception {
        connectAndHello();
        AppVariableMapper variables = mock(AppVariableMapper.class);
        var heartbeat = new CardWsHeartbeatService(registry, handler, mapper, variables, cards, mock(AppVariableTemplateResolver.class));
        card.setStatus(4);
        clearInvocations(session);

        heartbeat.sendHeartbeatOnce(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
        verifyNoInteractions(variables);
    }

    @Test
    void plaintextPongCannotRenewPresence() throws Exception {
        connectAndHello();
        WsEnvelope pong = pong();
        send(pong);
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void authenticatedPongRenewsOnlyExpectedToken() throws Exception {
        connectAndHello();
        WsEnvelope pong = encryptedPong();
        send(pong);
        verify(redis).execute(any(RedisScript.class), eq(List.of("cg:hb:card:" + CARD_ID, "cg:hb:token:" + TOKEN)),
                eq(TOKEN), anyString(), anyString(), eq(CARD_ID.toString()), eq(currentConnectionId));
        verify(session, never()).close(any());
    }

    @Test
    void invalidCiphertextCannotRenewPresence() throws Exception {
        connectAndHello();
        WsEnvelope pong = encryptedPong();
        pong.getCipher().setTag(WsCrypto.b64(new byte[16]));
        send(pong);
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void expiredPongCannotRenewPresence() throws Exception {
        connectAndHello();
        WsEnvelope pong = encryptedPong();
        pong.setTs(System.currentTimeMillis() - 60_000);
        send(pong);
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void replayedPongCannotRenewPresenceAgain() throws Exception {
        connectAndHello();
        WsEnvelope pong = encryptedPong();
        send(pong);
        clearInvocations(redis);
        send(pong);
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private void connectAndHello() throws Exception {
        handler.afterConnectionEstablished(session);
        WsEnvelope hello = new WsEnvelope();
        hello.setType("HELLO");
        hello.setTs(System.currentTimeMillis());
        hello.setNonce(UUID.randomUUID().toString());
        hello.setClientPubKey(WsCrypto.b64(WsCrypto.generateX25519KeyPair().getPublic().getEncoded()));
        send(hello);
        assertTrue(Boolean.TRUE.equals(attributes.get("cg.card.ws.helloDone")));
        clearInvocations(redis);
    }

    private String currentPayload() {
        return "{\"token\":\"" + TOKEN + "\",\"appId\":11,\"connectionId\":\"" + currentConnectionId + "\"}";
    }

    private WsEnvelope functionCall() {
        WsEnvelope call = new WsEnvelope();
        call.setType("FUNC_CALL");
        call.setPluginId("sample-plugin");
        call.setFunc("sample-function");
        call.setReqId("request-1");
        return call;
    }

    private WsEnvelope pong() {
        WsEnvelope pong = new WsEnvelope();
        pong.setType("PONG");
        pong.setTs(System.currentTimeMillis());
        pong.setSeq(1L);
        return pong;
    }

    private WsEnvelope encryptedPong() throws Exception {
        WsEnvelope pong = pong();
        byte[] aad = WsCrypto.utf8(attributes.get("cg.card.ws.connId") + "|" + pong.getSeq() + "|" + pong.getTs());
        WsCrypto.AesGcmPack encrypted = WsCrypto.aesGcmEncrypt((byte[]) attributes.get("cg.card.ws.sessionKey"), new byte[]{1}, aad);
        WsCipher cipher = new WsCipher();
        cipher.setIv(WsCrypto.b64(encrypted.iv()));
        cipher.setData(WsCrypto.b64(encrypted.ciphertext()));
        cipher.setTag(WsCrypto.b64(encrypted.tag()));
        pong.setCipher(cipher);
        return pong;
    }

    private void send(WsEnvelope envelope) throws Exception {
        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(envelope)));
    }
}
