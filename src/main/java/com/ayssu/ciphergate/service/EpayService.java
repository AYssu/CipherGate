package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.config.EpayConfig;
import org.springframework.util.StringUtils;
import com.ayssu.ciphergate.service.PaymentOrderService;
import com.ayssu.ciphergate.service.UserMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        if (!hasRequiredNotifyFields(params)
                || !epayConfig.getEpayPid().equals(params.get("pid"))
                || !StringUtils.hasText(epayConfig.getEpayKey())) {
            return false;
        }

        String receivedSign = params.get("sign");
        Map<String, String> sorted = new TreeMap<>();
        sorted.put("money", params.get("money"));
        sorted.put("name", params.get("name"));
        sorted.put("out_trade_no", params.get("out_trade_no"));
        sorted.put("pid", params.get("pid"));
        sorted.put("trade_no", params.get("trade_no"));
        sorted.put("trade_status", params.get("trade_status"));
        sorted.put("type", params.get("type"));

        String calculatedSign = calculateSign(sorted, epayConfig.getEpayKey());
        return !calculatedSign.isEmpty() && receivedSign.equalsIgnoreCase(calculatedSign);
    }

    private boolean hasRequiredNotifyFields(Map<String, String> params) {
        return List.of("money", "name", "out_trade_no", "pid", "trade_no", "trade_status", "type", "sign")
                .stream()
                .allMatch(field -> params.get(field) != null && !params.get(field).isBlank());
    }

    /**
     * 处理支付回调
     */
    public void handleNotify(Map<String, String> params) {
        if (!verifyNotifySign(params)) {
            throw new IllegalArgumentException("支付回调校验失败");
        }

        String tradeStatus = params.get("trade_status");
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        log.info("易支付回调: orderNo={}, tradeNo={}, status={}", orderNo, tradeNo, tradeStatus);

        if (!isSuccessStatus(tradeStatus)) {
            return;
        }

        PaymentOrder order = paymentOrderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("支付订单不存在");
        }

        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(params.get("money"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("支付金额格式错误");
        }
        if (callbackAmount.scale() > 2 || callbackAmount.signum() < 0) {
            throw new IllegalArgumentException("支付金额格式错误");
        }
        long callbackAmountFen = callbackAmount.movePointRight(2).longValueExact();
        if (order.getTotalAmount() == null || order.getTotalAmount() != callbackAmountFen) {
            throw new IllegalArgumentException("支付金额与订单金额不一致");
        }

        paymentOrderService.handlePaymentSuccess(orderNo, tradeNo);
    }

    public static boolean isSuccessStatus(String tradeStatus) {
        return "TRADE_SUCCESS".equals(tradeStatus)
                || "TRADE_FINISHED".equals(tradeStatus)
                || "FINISHED".equals(tradeStatus);
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
