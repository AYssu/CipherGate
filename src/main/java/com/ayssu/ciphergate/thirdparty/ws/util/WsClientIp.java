package com.ayssu.ciphergate.thirdparty.ws.util;

import com.ayssu.ciphergate.thirdparty.ws.WsHandshakeIpInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

public final class WsClientIp {
    private WsClientIp() {}

    public static String resolve(WebSocketSession session) {
        if (session == null) {
            return "";
        }
        try {
            Object attrIp = session.getAttributes().get(WsHandshakeIpInterceptor.ATTR_CLIENT_IP);
            if (attrIp instanceof String s && StringUtils.hasText(s)) {
                return s.trim();
            }
            if (session.getRemoteAddress() != null && session.getRemoteAddress().getAddress() != null) {
                return session.getRemoteAddress().getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
