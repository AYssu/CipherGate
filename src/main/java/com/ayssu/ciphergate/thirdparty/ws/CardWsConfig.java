package com.ayssu.ciphergate.thirdparty.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 卡密 WebSocket 配置。
 * 端点: /api/v1/card/ws?token=xxx
 */
@Configuration
@RequiredArgsConstructor
public class CardWsConfig implements WebSocketConfigurer {

    private final CardWsHandler cardWsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cardWsHandler, "/api/v1/card/ws")
                .setAllowedOriginPatterns("*");
    }
}
