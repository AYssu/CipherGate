package com.ayssu.ciphergate.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/** SK payment protocol: server-to-server signed JSON creation, followed by checkout navigation. */
public final class SkPaymentGateway {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private SkPaymentGateway() {}

    public static String createCheckout(String gateway, Map<String, String> fields, String key) {
        URI base = httpUri(gateway);
        if (base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("支付网关地址不能包含查询参数或片段");
        }
        Map<String, String> params = new TreeMap<>(fields);
        SkPaymentSignature.seal(params, key, "payment.create");
        String endpoint = gateway.replaceAll("/+$", "") + "/pay/submit";
        HttpRequest request = HttpRequest.newBuilder(httpUri(endpoint)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(params))).build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("支付网关请求失败，请稍后重试");
            JSONObject body;
            try {
                body = JSON.parseObject(response.body());
            } catch (RuntimeException malformed) {
                throw new IllegalStateException("支付网关返回格式无效");
            }
            if (body == null || !(Boolean.TRUE.equals(body.getBoolean("success"))
                    || Integer.valueOf(200).equals(body.getInteger("code")))
                    || !(body.get("data") instanceof String checkout) || checkout.isBlank()) {
                throw new IllegalStateException("支付网关未返回有效付款地址");
            }
            httpUri(checkout);
            return checkout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("支付请求被中断，请查询订单后重试");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("支付网关连接失败，请查询订单后重试");
        }
    }

    private static URI httpUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("支付地址须为有效的 HTTP 或 HTTPS 地址");
        }
    }
}
