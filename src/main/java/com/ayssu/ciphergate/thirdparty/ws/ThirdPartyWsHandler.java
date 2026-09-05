package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartySignatureVerifier;
import com.ayssu.ciphergate.thirdparty.ws.crypto.WsCrypto;
import com.ayssu.ciphergate.thirdparty.ws.model.WsCipher;
import com.ayssu.ciphergate.thirdparty.ws.model.WsEnvelope;
import com.ayssu.ciphergate.thirdparty.ws.model.WsAuthPayload;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsAuthService;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsDeviceBindService;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsLoginRecorder;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsSessionKickService;
import com.ayssu.ciphergate.thirdparty.ws.service.ThirdPartyWsHeartbeatService;
import com.ayssu.ciphergate.thirdparty.ws.service.ThirdPartyWsSessionRegistry;
import com.ayssu.ciphergate.thirdparty.ws.service.WsNonceService;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ayssu.ciphergate.thirdparty.ws.util.WsClientIp;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WS-only third-party auth channel.
 *
 * Flow:
 * 1) HELLO (plain): appKey + clientPubKey(x509,b64) + ts/nonce/seq
 * 2) HELLO_ACK (plain): connId + serverPubKey + serverNonce
 * 3) AUTH (encrypted): {appKey, appSig, username, password, deviceId, deviceName, deviceOs, ts, nonce, seq}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyWsHandler extends TextWebSocketHandler {

    private static final long MAX_SKEW_MS = 15_000L;
    private static final byte[] HKDF_INFO = WsCrypto.utf8("cg-ws-v1");

    private static final String ATTR_CONN_ID = "cg.ws.connId";
    private static final String ATTR_APP = "cg.ws.app";
    private static final String ATTR_CLIENT_PUB = "cg.ws.clientPub";
    private static final String ATTR_SERVER_KP = "cg.ws.serverKp";
    private static final String ATTR_SERVER_NONCE = "cg.ws.serverNonce";
    private static final String ATTR_SESSION_KEY = "cg.ws.sessionKey";
    private static final String ATTR_AUTHED = "cg.ws.authed";
    private static final String ATTR_LAST_SEQ = "cg.ws.lastSeq";
    /** 单调递增；每发一条变量 HEARTBEAT +1，用于 HKDF 子密钥。 */
    public static final String ATTR_VAR_PACKET_SEQ = "cg.ws.varPacketSeq";
    private static final String ATTR_APP_USER_ID = "cg.ws.appUserId";
    private static final String ATTR_WS_CONNECTED_AT_MS = "cg.ws.connectedAtMs";
    private static final String ATTR_WS_RESUMED_CARRY_SEC = "cg.ws.resumedCarrySec";
    /** 建连时解析的客户端 IP（升级请求 RemoteAddress / X-Forwarded-For 等），AUTH 与绑定复用 */
    private static final String ATTR_CLIENT_IP = "cg.ws.clientIp";
    private static final String ATTR_DEVICE_ID = "cg.ws.deviceId";
    private static final String ATTR_DEVICE_NAME = "cg.ws.deviceName";
    private static final String ATTR_DEVICE_OS = "cg.ws.deviceOs";

    private final ObjectMapper objectMapper;
    private final AppUserWsAuthService appUserWsAuthService;
    private final WsNonceService wsNonceService;
    private final ThirdPartyWsSessionRegistry sessionRegistry;
    private final AppUserWsLoginRecorder appUserWsLoginRecorder;
    private final AppUserWsPresenceRegistry appUserWsPresenceRegistry;
    private final AppUserWsDeviceBindService appUserWsDeviceBindService;
    private final AppUserWsSessionKickService appUserWsSessionKickService;
    private final ThirdPartyWsHeartbeatService thirdPartyWsHeartbeatService;
    private final FunctionRuntimeService functionRuntimeService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String connId = UUID.randomUUID().toString().replace("-", "");
        session.getAttributes().put(ATTR_CONN_ID, connId);
        session.getAttributes().put(ATTR_AUTHED, false);
        session.getAttributes().put(ATTR_CLIENT_IP, WsClientIp.resolve(session));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "CONNECTED",
                "connId", connId,
                "ts", Instant.now().toEpochMilli()
        ))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object connIdObj = session.getAttributes().get(ATTR_CONN_ID);
        if (connIdObj instanceof String connId) {
            sessionRegistry.remove(connId);
            appUserWsPresenceRegistry.unregister(connId);
        }
        super.afterConnectionClosed(session, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsEnvelope env;
        try {
            env = objectMapper.readValue(message.getPayload(), WsEnvelope.class);
        } catch (Exception e) {
            close(session, 1003, "BAD_JSON");
            return;
        }

        String type = env.getType();
        if (!StringUtils.hasText(type)) {
            close(session, 1003, "MISSING_TYPE");
            return;
        }

        switch (type) {
            case "HELLO" -> handleHello(session, env);
            case "AUTH" -> handleAuth(session, env);
            case "PING" -> session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            case "FUNC_CALL" -> handleFuncCall(session, env);
            default -> close(session, 1008, "UNSUPPORTED");
        }
    }

    private void handleHello(WebSocketSession session, WsEnvelope env) throws Exception {
        long now = Instant.now().toEpochMilli();
        Long ts = env.getTs();
        if (ts == null || Math.abs(now - ts) > MAX_SKEW_MS) {
            close(session, 1008, "EXPIRED");
            return;
        }
        if (!wsNonceService.markIfNew("hello", env.getNonce())) {
            close(session, 1008, "REPLAY");
            return;
        }

        if (!StringUtils.hasText(env.getAppKey()) || !StringUtils.hasText(env.getClientPubKey())) {
            close(session, 1008, "MISSING_FIELDS");
            return;
        }

        Application app;
        try {
            app = appUserWsAuthService.requireActiveAppByKey(env.getAppKey());
        } catch (Exception e) {
            close(session, 1008, "APP_INVALID");
            return;
        }

        byte[] clientPubBytes;
        try {
            clientPubBytes = WsCrypto.b64d(env.getClientPubKey());
        } catch (Exception e) {
            close(session, 1008, "BAD_PUBKEY");
            return;
        }
        PublicKey clientPub = WsCrypto.decodeX25519PublicKey(clientPubBytes);
        KeyPair serverKp = WsCrypto.generateX25519KeyPair();

        byte[] shared = WsCrypto.ecdhX25519(serverKp, clientPub);
        byte[] serverNonce = randomNonceB64();
        byte[] salt = WsCrypto.hmacSha256(WsCrypto.utf8(env.getNonce()), serverNonce); // deterministic salt mix
        byte[] sessionKey = WsCrypto.hkdfSha256(shared, salt, HKDF_INFO, 32);

        session.getAttributes().put(ATTR_APP, app);
        session.getAttributes().put(ATTR_CLIENT_PUB, env.getClientPubKey());
        session.getAttributes().put(ATTR_SERVER_KP, serverKp);
        session.getAttributes().put(ATTR_SERVER_NONCE, WsCrypto.b64(serverNonce));
        session.getAttributes().put(ATTR_SESSION_KEY, sessionKey);
        session.getAttributes().put(ATTR_LAST_SEQ, 0L);
        session.getAttributes().put(ATTR_VAR_PACKET_SEQ, 0L);

        WsEnvelope ack = new WsEnvelope();
        ack.setType("HELLO_ACK");
        ack.setConnId((String) session.getAttributes().get(ATTR_CONN_ID));
        ack.setTs(now);
        ack.setServerPubKey(WsCrypto.b64(serverKp.getPublic().getEncoded()));
        ack.setServerNonce((String) session.getAttributes().get(ATTR_SERVER_NONCE));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    private void handleAuth(WebSocketSession session, WsEnvelope env) throws Exception {
        if (Boolean.TRUE.equals(session.getAttributes().get(ATTR_AUTHED))) {
            close(session, 1008, "ALREADY_AUTHED");
            return;
        }

        byte[] sessionKey = (byte[]) session.getAttributes().get(ATTR_SESSION_KEY);
        Application app = (Application) session.getAttributes().get(ATTR_APP);
        String clientPubKeyB64 = (String) session.getAttributes().get(ATTR_CLIENT_PUB);
        String serverPubKeyB64 = null;
        Object kpObj = session.getAttributes().get(ATTR_SERVER_KP);
        if (kpObj instanceof KeyPair kp) {
            serverPubKeyB64 = WsCrypto.b64(kp.getPublic().getEncoded());
        }
        String serverNonceB64 = (String) session.getAttributes().get(ATTR_SERVER_NONCE);

        if (sessionKey == null || app == null || !StringUtils.hasText(clientPubKeyB64) || !StringUtils.hasText(serverPubKeyB64) || !StringUtils.hasText(serverNonceB64)) {
            close(session, 1008, "NO_HELLO");
            return;
        }
        if (env.getCipher() == null || !StringUtils.hasText(env.getCipher().getIv()) || !StringUtils.hasText(env.getCipher().getData())) {
            close(session, 1008, "MISSING_CIPHER");
            return;
        }

        // decrypt payload
        WsAuthPayload payload;
        try {
            WsCipher c = env.getCipher();
            byte[] iv = WsCrypto.b64d(c.getIv());
            byte[] ct = WsCrypto.b64d(c.getData());
            byte[] tag = StringUtils.hasText(c.getTag()) ? WsCrypto.b64d(c.getTag()) : new byte[0];
            byte[] plain = WsCrypto.aesGcmDecrypt(sessionKey, iv, ct, tag, null);
            payload = objectMapper.readValue(plain, WsAuthPayload.class);
        } catch (Exception e) {
            close(session, 1008, "DECRYPT_FAIL");
            return;
        }

        // basic anti-replay for AUTH
        long now = Instant.now().toEpochMilli();
        if (payload.getTs() == null || Math.abs(now - payload.getTs()) > MAX_SKEW_MS) {
            close(session, 1008, "EXPIRED");
            return;
        }
        if (!wsNonceService.markIfNew("auth:" + app.getAppKey(), payload.getNonce())) {
            close(session, 1008, "REPLAY");
            return;
        }

        // seq monotonic per connection (in-memory)
        long lastSeq = session.getAttributes().get(ATTR_LAST_SEQ) instanceof Long l ? l : 0L;
        long seq = payload.getSeq() == null ? 0L : payload.getSeq();
        if (seq <= lastSeq) {
            close(session, 1008, "BAD_SEQ");
            return;
        }
        session.getAttributes().put(ATTR_LAST_SEQ, seq);

        // verify appSig
        if (!StringUtils.hasText(payload.getAppKey()) || !payload.getAppKey().equals(app.getAppKey())) {
            close(session, 1008, "APP_MISMATCH");
            return;
        }
        String connId = (String) session.getAttributes().get(ATTR_CONN_ID);
        String signString = buildAppSigString(payload.getAppKey(), connId, clientPubKeyB64, serverPubKeyB64, serverNonceB64, payload.getTs(), payload.getNonce(), seq);
        String expected = ThirdPartySignatureVerifier.hmacSha256Hex(app.getAppSecret(), signString);
        if (!expected.equalsIgnoreCase(payload.getAppSig())) {
            close(session, 1008, "BAD_APPSIG");
            return;
        }

        String deviceId = StringUtils.hasText(payload.getDeviceId()) ? payload.getDeviceId().trim() : null;
        String deviceName = StringUtils.hasText(payload.getDeviceName()) ? payload.getDeviceName().trim() : null;
        String deviceOs = StringUtils.hasText(payload.getDeviceOs()) ? payload.getDeviceOs().trim() : null;
        if (!StringUtils.hasText(deviceId) || deviceId.length() > 255
                || !StringUtils.hasText(deviceName) || deviceName.length() > 100
                || !StringUtils.hasText(deviceOs) || deviceOs.length() > 50) {
            close(session, 1008, "BAD_DEVICE");
            return;
        }

        // verify AppUser credentials under appId
        AppUser u;
        try {
            u = appUserWsAuthService.loginAppUser(app.getId(), payload.getUsername(), payload.getPassword());
        } catch (Exception e) {
            close(session, 1008, "AUTH_FAIL");
            return;
        }

        // 会员：付费/试用+付费需有效到期；免费模式不校验会员时间
        boolean freeApp = app.getBusinessModel() != null && app.getBusinessModel() == 2;
        if (!freeApp) {
            LocalDateTime memberExp = u.getMemberExpiresAt();
            LocalDateTime nowLdt = LocalDateTime.now();
            if (memberExp == null || !memberExp.isAfter(nowLdt)) {
                close(session, 1008, "MEMBER_EXPIRED");
                return;
            }
        }

        String clientIp = clientIpFromSession(session);
        try {
            appUserWsDeviceBindService.bindOrTouchOnWsLogin(app.getId(), u.getId(), deviceId, deviceName, deviceOs, clientIp);
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            if ("DEVICE_BANNED".equals(code)) {
                close(session, 1008, "DEVICE_BANNED");
            } else if ("DEVICE_CONFLICT".equals(code)) {
                close(session, 1008, "DEVICE_CONFLICT");
            } else {
                close(session, 1008, "BIND_FAIL");
            }
            return;
        } catch (Exception e) {
            log.warn("ws device bind failed: {}", e.getMessage());
            close(session, 1008, "BIND_FAIL");
            return;
        }

        // 同一终端用户仅保留一条已 AUTH 的 WS：新登录挤掉其它设备/其它连接
        appUserWsSessionKickService.kickByAppUserId(u.getId(), null, AppUserWsSessionKickService.KICK_LOGIN_ELSEWHERE);

        session.getAttributes().put(ATTR_AUTHED, true);
        sessionRegistry.add(connId, session);

        long connectedAtMs = Instant.now().toEpochMilli();
        appUserWsLoginRecorder.recordSuccessfulLogin(app.getId(), u.getId(), clientIp, deviceId);
        AppUserWsPresenceRegistry.WsSessionTicket ticket =
                appUserWsPresenceRegistry.register(connId, u.getId(), deviceId, clientIp, connectedAtMs);
        session.getAttributes().put(ATTR_APP_USER_ID, u.getId());
        session.getAttributes().put(ATTR_WS_CONNECTED_AT_MS, ticket != null ? ticket.connectedAtEpochMs() : connectedAtMs);
        session.getAttributes().put(ATTR_WS_RESUMED_CARRY_SEC, ticket != null ? ticket.resumedCarrySeconds() : 0L);
        session.getAttributes().put(ATTR_DEVICE_ID, deviceId);
        session.getAttributes().put(ATTR_DEVICE_NAME, deviceName);
        session.getAttributes().put(ATTR_DEVICE_OS, deviceOs);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("type", "AUTH_OK");
        ok.put("connId", connId);
        ok.put("appId", app.getId());
        ok.put("appUserId", u.getId());
        ok.put("username", u.getUsername());
        ok.put("ts", now);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ok)));
        thirdPartyWsHeartbeatService.sendHeartbeatOnce(session);
    }

    /**
     * 处理 FUNC_CALL：客户端调用插件函数
     */
    private void handleFuncCall(WebSocketSession session, WsEnvelope env) throws Exception {
        // 检查是否已 AUTH
        if (!Boolean.TRUE.equals(session.getAttributes().get(ATTR_AUTHED))) {
            sendFuncError(session, env.getReqId(), env.getFunc(), "NOT_AUTHED", "未认证，无法调用函数");
            return;
        }

        // 校验参数
        String funcName = env.getFunc();
        if (!StringUtils.hasText(funcName)) {
            sendFuncError(session, env.getReqId(), null, "MISSING_FUNC", "缺少函数名称");
            return;
        }

        Map<String, Object> params = env.getParams();
        if (params == null) {
            params = Map.of();
        }

        // 优先使用请求中指定的 pluginId，否则尝试自动查找
        String pluginId = env.getPluginId();
        if (!StringUtils.hasText(pluginId)) {
            // 自动查找：遍历所有已注册的函数插件，找到包含该函数的插件
            pluginId = functionRuntimeService.findPluginByFunction(funcName);
            if (!StringUtils.hasText(pluginId)) {
                sendFuncError(session, env.getReqId(), funcName, "FUNC_NOT_FOUND", "未找到包含函数 " + funcName + " 的插件");
                return;
            }
        }

        // 执行函数
        FunctionResult result = functionRuntimeService.executeFunction(pluginId, funcName, params);

        // 发送响应
        if (result.success()) {
            WsEnvelope resp = new WsEnvelope();
            resp.setType("FUNC_RESULT");
            resp.setReqId(env.getReqId());
            resp.setFunc(funcName);
            resp.setData(result.data());
            resp.setTs(Instant.now().toEpochMilli());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        } else {
            sendFuncError(session, env.getReqId(), funcName, result.code(), result.message());
        }
    }

    private void sendFuncError(WebSocketSession session, String reqId, String func, String code, String message) throws Exception {
        WsEnvelope resp = new WsEnvelope();
        resp.setType("FUNC_ERROR");
        resp.setReqId(reqId);
        resp.setFunc(func);
        resp.setCode(code);
        resp.setMessage(message);
        resp.setTs(Instant.now().toEpochMilli());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
    }

    private String buildAppSigString(String appKey,
                                    String connId,
                                    String clientPubKeyB64,
                                    String serverPubKeyB64,
                                    String serverNonceB64,
                                    long ts,
                                    String nonce,
                                    long seq) {
        // Canonical, stable, easy to implement in Java/C++/JS
        return appKey + "\n"
                + connId + "\n"
                + clientPubKeyB64 + "\n"
                + serverPubKeyB64 + "\n"
                + serverNonceB64 + "\n"
                + ts + "\n"
                + nonce + "\n"
                + seq;
    }

    private byte[] randomNonceB64() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    private static String clientIpFromSession(WebSocketSession session) {
        Object o = session.getAttributes().get(ATTR_CLIENT_IP);
        if (o instanceof String s && StringUtils.hasText(s)) {
            return s;
        }
        return WsClientIp.resolve(session);
    }

    private void close(WebSocketSession session, int code, String reason) {
        try {
            session.close(new CloseStatus(code, reason));
        } catch (Exception ignored) {
        }
    }
}

