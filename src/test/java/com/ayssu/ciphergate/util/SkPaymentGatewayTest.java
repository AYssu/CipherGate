package com.ayssu.ciphergate.util;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkPaymentGatewayTest {
    @Test void createsSignedJsonOrderAndReturnsCheckoutWithoutExposingKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new AtomicReference<Map<String,String>>();
        var method = new AtomicReference<String>();
        server.createContext("/pay/submit", exchange -> {
            method.set(exchange.getRequestMethod());
            received.set(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                new com.alibaba.fastjson2.TypeReference<Map<String,String>>() {}));
            byte[] body = "{\"code\":200,\"success\":true,\"data\":\"https://pay.example.test/submit/test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            String url = SkPaymentGateway.createCheckout("http://127.0.0.1:"+server.getAddress().getPort(),
                Map.of("pid","123","pay_type","alipay","money","1.01","name","会员 &=+ 中文","return_url","","notify_url","https://app.example.test/notify"),"test-secret");
            assertEquals("https://pay.example.test/submit/test-token",url);
            assertEquals("POST",method.get());
            assertTrue(SkPaymentSignature.verify(received.get(),"test-secret","payment.create",Instant.now().getEpochSecond()));
            assertEquals("",received.get().get("return_url"));
            assertEquals("会员 &=+ 中文",received.get().get("name"));
            assertFalse(received.get().containsValue("test-secret"));
        } finally {server.stop(0);}
    }

    @Test void rejectsLegacyGatewayFailureAndExecutableCheckout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var reply = new AtomicReference<>("{\"code\":400,\"message\":\"private gateway details\"}");
        server.createContext("/pay/submit",exchange->{exchange.getRequestBody().readAllBytes();byte[] body=reply.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.start();
        try {
            String gateway="http://127.0.0.1:"+server.getAddress().getPort();
            var failure=assertThrows(IllegalStateException.class,()->SkPaymentGateway.createCheckout(gateway,Map.of(),"test-secret"));
            assertFalse(failure.getMessage().contains("private"));
            reply.set("{\"code\":200,\"data\":\"javascript:alert(1)\"}");
            assertThrows(IllegalArgumentException.class,()->SkPaymentGateway.createCheckout(gateway,Map.of(),"test-secret"));
        } finally {server.stop(0);}
    }
}
