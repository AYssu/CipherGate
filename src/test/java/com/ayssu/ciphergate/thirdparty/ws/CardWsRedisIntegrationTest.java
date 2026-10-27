package com.ayssu.ciphergate.thirdparty.ws;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.thirdparty.ws.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes production Lua against the isolated test Redis, without network/application credentials. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named="CIPHERGATE_BOOT_JDBC_URL", matches=".+/ciphergate_boot_test.*")
class CardWsRedisIntegrationTest {
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    LicenseKeyMapper cards=mock(LicenseKeyMapper.class);
    ApplicationMapper apps=mock(ApplicationMapper.class);
    CardWsSessionRegistry registry=new CardWsSessionRegistry();
    CardWsHandler handler;
    long cardId=Math.abs(UUID.randomUUID().getMostSignificantBits());
    String token="test-"+UUID.randomUUID();
    String cardKey="cg:hb:card:"+cardId;
    String tokenKey="cg:hb:token:"+token;
    @BeforeAll static void connectRedis() {
        connection=new LettuceConnectionFactory(System.getenv().getOrDefault("CIPHERGATE_TEST_REDIS_HOST","127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("CIPHERGATE_TEST_REDIS_PORT","6379")));
        connection.afterPropertiesSet();connection.start();redis=new StringRedisTemplate(connection);redis.afterPropertiesSet();
    }
    @AfterAll static void close(){connection.destroy();}
    @BeforeEach void setup() {
        var card=new LicenseKey();card.setId(cardId);card.setAppId(7L);card.setStatus(2);card.setHeartbeatInterval(60);
        var app=new Application();app.setId(7L);app.setStatus(1);
        when(cards.selectById(cardId)).thenReturn(card);when(apps.selectById(7L)).thenReturn(app);
        handler=new CardWsHandler(redis,cards,apps,registry,new ObjectMapper(),mock(WsNonceService.class),mock(FunctionRuntimeService.class));
        redis.opsForValue().set(cardKey,"{\"appId\":7,\"token\":\""+token+"\"}",Duration.ofSeconds(120));
        redis.opsForValue().set(tokenKey,String.valueOf(cardId),Duration.ofSeconds(120));
    }
    @AfterEach void cleanup(){redis.delete(List.of(cardKey,tokenKey));}
    WebSocketSession connect()throws Exception {
        var session=mock(WebSocketSession.class);var attrs=new HashMap<String,Object>();
        when(session.getAttributes()).thenReturn(attrs);when(session.getUri()).thenReturn(URI.create("ws://localhost/api/v1/card/ws?token="+token));
        when(session.isOpen()).thenReturn(true);handler.afterConnectionEstablished(session);return session;
    }
    @Test void sameTokenReconnectAndLateOldCloseCannotEraseReplacementOrRenewItsHeartbeat()throws Exception {
        var old=connect();String oldConn=(String)old.getAttributes().get("cg.card.ws.connId");
        var replacement=connect();String newConn=(String)replacement.getAttributes().get("cg.card.ws.connId");
        assertNotEquals(oldConn,newConn);
        handler.updateRedisHeartbeat(old);
        var payload=new ObjectMapper().readTree(redis.opsForValue().get(cardKey));
        assertEquals(newConn,payload.get("connectionId").asText());assertFalse(payload.has("lastHeartbeatAt"));
        handler.afterConnectionClosed(old,CloseStatus.NORMAL);
        assertNotNull(redis.opsForValue().get(cardKey));assertEquals(String.valueOf(cardId),redis.opsForValue().get(tokenKey));
        assertTrue(handler.validateEntitlement(replacement));
        handler.afterConnectionClosed(replacement,CloseStatus.NORMAL);
        assertNull(redis.opsForValue().get(cardKey));assertNull(redis.opsForValue().get(tokenKey));
    }
    @Test void connectPreservesExpiryAndOnlyCurrentPongRenewsBothIndexes()throws Exception {
        long before=redis.getExpire(cardKey);var session=connect();
        assertTrue(redis.getExpire(cardKey)<=before);assertTrue(redis.getExpire(cardKey)>0);
        redis.expire(cardKey,Duration.ofSeconds(10));redis.expire(tokenKey,Duration.ofSeconds(10));
        handler.updateRedisHeartbeat(session);
        assertTrue(redis.getExpire(cardKey)>300);assertTrue(redis.getExpire(tokenKey)>300);
        assertNotNull(new ObjectMapper().readTree(redis.opsForValue().get(cardKey)).get("lastHeartbeatAt"));
    }
}
