package com.ayssu.ciphergate.thirdparty.ws.util;

import com.ayssu.ciphergate.thirdparty.ws.WsHandshakeIpInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

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
            List<String> xff = session.getHandshakeHeaders().get("X-Forwarded-For");
            if (xff != null && !xff.isEmpty() && StringUtils.hasText(xff.get(0))) {
                String first = xff.get(0).split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return first;
                }
            }
            List<String> realIp = session.getHandshakeHeaders().get("X-Real-IP");
            if (realIp != null && !realIp.isEmpty() && StringUtils.hasText(realIp.get(0))) {
                return realIp.get(0).trim();
            }
            if (session.getRemoteAddress() != null && session.getRemoteAddress().getAddress() != null) {
                return session.getRemoteAddress().getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
