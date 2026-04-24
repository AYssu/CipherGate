package com.ayssu.ciphergate.thirdparty.ws;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 在 WS 握手阶段解析客户端 IP，写入会话属性，供后续 AUTH 复用。
 */
@Slf4j
@Component
public class WsHandshakeIpInterceptor implements HandshakeInterceptor {

    public static final String ATTR_CLIENT_IP = "cg.ws.clientIp";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            String ip = resolveIp(request);
            if (StringUtils.hasText(ip)) {
                attributes.put(ATTR_CLIENT_IP, ip.trim());
            }
        } catch (Exception e) {
            log.debug("resolve ws handshake ip failed", e);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String resolveIp(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest req = servletRequest.getServletRequest();
            String xff = trimToNull(req.getHeader("X-Forwarded-For"));
            if (xff != null) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff;
            }
            String realIp = trimToNull(req.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
            String remote = trimToNull(req.getRemoteAddr());
            if (remote != null) {
                return remote;
            }
        }
        return request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "";
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
