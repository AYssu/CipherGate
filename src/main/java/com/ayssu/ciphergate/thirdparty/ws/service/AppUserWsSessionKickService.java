package com.ayssu.ciphergate.thirdparty.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 主动断开终端用户已建立的第三方 WS：管理端封禁、或新 AUTH 挤掉旧会话等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserWsSessionKickService {

    public static final CloseStatus KICK_DEVICE_BANNED = new CloseStatus(1008, "DEVICE_BANNED");
    /** 同一账号在其他处完成 AUTH，本连接被关闭（与 AUTH 阶段 policy violation 一致） */
    public static final CloseStatus KICK_LOGIN_ELSEWHERE = new CloseStatus(1008, "LOGIN_ELSEWHERE");

    private final ThirdPartyWsSessionRegistry sessionRegistry;
    private final AppUserWsPresenceRegistry presenceRegistry;

    /**
     * @param appUserId   终端用户 ID
     * @param deviceId    非空时仅断开该 deviceId 上的会话；为空则断开该用户全部会话
     */
    public void kickByAppUserId(Long appUserId, String deviceId) {
        kickByAppUserId(appUserId, deviceId, KICK_DEVICE_BANNED);
    }

    /**
     * @param deviceId 非空时仅匹配该设备上的会话；空表示该用户下全部会话
     * @param status   WebSocket 关闭状态（客户端可据 reason 区分封禁 / 被挤下线）
     */
    public void kickByAppUserId(Long appUserId, String deviceId, CloseStatus status) {
        if (appUserId == null || status == null) {
            return;
        }
        String matchDev = StringUtils.hasText(deviceId) ? deviceId.trim() : null;
        var snap = presenceRegistry.snapshot(appUserId);
        if (!snap.isOnline()) {
            return;
        }
        for (AppUserWsPresenceRegistry.WsSessionTicket t : snap.getSessions()) {
            if (matchDev != null && !matchDev.equals(t.deviceId())) {
                continue;
            }
            WebSocketSession session = sessionRegistry.get(t.connId());
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.close(status);
            } catch (Exception e) {
                log.warn("close ws failed, connId={}, appUserId={}, reason={}", t.connId(), appUserId, status.getReason(), e);
            }
        }
    }
}
