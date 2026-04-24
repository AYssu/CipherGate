package com.ayssu.ciphergate.thirdparty.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class ThirdPartyWsConfig implements WebSocketConfigurer {

    private final ThirdPartyWsHandler thirdPartyWsHandler;
    private final WsHandshakeIpInterceptor wsHandshakeIpInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(thirdPartyWsHandler, "/api/v1/ws")
                .addInterceptors(wsHandshakeIpInterceptor)
                .setAllowedOriginPatterns("*");
    }
}

