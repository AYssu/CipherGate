package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.config.EpayConfig;
import com.ayssu.ciphergate.service.PaymentOrderService;
import com.ayssu.ciphergate.service.UserMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpayService {

    private final EpayConfig epayConfig;
    private final PaymentOrderService paymentOrderService;
    private final UserMembershipService userMembershipService;

    /**
     * 创建支付订单并返回支付跳转URL
     * @return 支付URL，返回null表示余额支付成功（无需跳转）
     */
    public String createPayment(Long userId, Long productId, int quantity, String productName, Long totalAmount) {
        PaymentOrder order = paymentOrderService.createOrder(userId, productId, quantity);
        return null;
    }

    /**
     * 创建易支付订单并返回支付跳转URL
     */
    public String createEpayOrder(Long userId, String productName, Long amountFen, String orderNo) {
        if (epayConfig.getEpayPid() == null || epayConfig.getEpayPid().isEmpty()) {
            throw new RuntimeException("支付系统未配置");
        }

        String money = String.format("%.2f", amountFen / 100.0);

        TreeMap<String, String> params = new TreeMap<>();
        params.put("pid", epayConfig.getEpayPid());
        params.put("type", "alipay");
        params.put("notify_url", epayConfig.getEpayNotifyUrl());
        params.put("return_url", epayConfig.getEpayReturnUrl());
        params.put("out_trade_no", orderNo);
        params.put("name", productName);
        params.put("money", money);
        params.put("sitename", "CipherGate");

        String sign = calculateSign(params, epayConfig.getEpayKey());
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) query.append("&");
            query.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return epayConfig.getEpayUrl() + "/submit.php?" + query;
    }

    /**
     * 验证回调签名（与易支付服务端一致：只用 money/name/out_trade_no/pid/trade_no/trade_status/type）
     */
    public boolean verifyNotifySign(Map<String, String> params) {
        String receivedSign = params.get("sign");
        if (receivedSign == null || receivedSign.isEmpty()) return false;

        Map<String, String> sorted = new TreeMap<>();
        sorted.put("money", params.get("money"));
        sorted.put("name", params.get("name"));
        sorted.put("out_trade_no", params.get("out_trade_no"));
        sorted.put("pid", params.get("pid"));
        sorted.put("trade_no", params.get("trade_no"));
        sorted.put("trade_status", params.get("trade_status"));
        sorted.put("type", params.get("type"));

        String calculatedSign = calculateSign(sorted, epayConfig.getEpayKey());
        log.info("签名验证: 期望={}, 计算={}", receivedSign, calculatedSign);
        return receivedSign.equalsIgnoreCase(calculatedSign);
    }

    /**
     * 处理支付回调
     */
    public void handleNotify(Map<String, String> params) {
        String tradeStatus = params.get("trade_status");
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");

        log.info("易支付回调: orderNo={}, tradeNo={}, status={}", orderNo, tradeNo, tradeStatus);

        if ("TRADE_SUCCESS".equals(tradeStatus) || "FINISHED".equals(tradeStatus)) {
            paymentOrderService.handlePaymentSuccess(orderNo, tradeNo);
        }
    }

    /**
     * 易支付签名: 密钥直接拼在末尾
     * 格式: key1=value1&key2=value2密钥
     */
    private String calculateSign(Map<String, String> params, String merchantKey) {
        try {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey())) {
                    if (!first) sb.append("&");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
            }
            sb.append(merchantKey);

            String signStr = sb.toString();
            log.info("签名原文: {}", signStr);

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("签名计算失败", e);
            return "";
        }
    }

    private String generateOrderNo() {
        return "EP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
