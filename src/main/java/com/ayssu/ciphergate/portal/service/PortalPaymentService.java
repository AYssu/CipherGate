package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.SystemConfigService;
import com.ayssu.ciphergate.portal.entity.ApplicationEpayConfig;
import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.ayssu.ciphergate.portal.mapper.ApplicationEpayConfigMapper;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
import com.ayssu.ciphergate.portal.mapper.PortalPricingPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalPaymentService {

    private final PortalPaymentOrderMapper orderMapper;
    private final PortalPricingPlanMapper planMapper;
    private final ApplicationEpayConfigMapper epayConfigMapper;
    private final ApplicationMapper applicationMapper;
    private final AppUserMapper appUserMapper;
    private final SystemConfigService systemConfigService;

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Transactional
    public PortalPaymentOrder createOrder(Long appUserId, Long appId, Long planId) {
        // 校验应用支付功能
        Application app = applicationMapper.selectById(appId);
        if (app == null || !Boolean.TRUE.equals(app.getPortalPaymentEnabled())) {
            throw new IllegalArgumentException("该应用未开启支付功能");
        }

        // 校验定价方案
        PortalPricingPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getAppId().equals(appId) || !Boolean.TRUE.equals(plan.getEnabled())) {
            throw new IllegalArgumentException("定价方案不存在或已停用");
        }

        // 创建订单
        PortalPaymentOrder order = new PortalPaymentOrder();
        order.setOrderNo(generateOrderNo());
        order.setAppUserId(appUserId);
        order.setAppId(appId);
        order.setPlanId(planId);
        order.setPlanName(plan.getPlanName());
        order.setDurationDays(plan.getDurationDays());
        order.setAmountFen(plan.getPriceFen());
        order.setPaymentChannel("alipay");
        order.setStatus(0);

        orderMapper.insert(order);

        // 获取易支付配置
        ApplicationEpayConfig epayConfig = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
                .eq(ApplicationEpayConfig::getEnabled, true)
        );

        if (epayConfig == null) {
            throw new IllegalArgumentException("该应用未配置支付网关");
        }

        // 生成支付URL（简化版，实际需要调用易支付API）
        String payUrl = buildPayUrl(epayConfig, order);
        order.setPayUrl(payUrl);
        orderMapper.updateById(order);

        log.info("创建门户支付订单: orderNo={}, appId={}, planId={}, amount={}",
            order.getOrderNo(), appId, planId, plan.getPriceFen());

        return order;
    }

    public List<PortalPaymentOrder> getOrders(Long appUserId, int page, int size) {
        return orderMapper.selectList(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getAppUserId, appUserId)
                .orderByDesc(PortalPaymentOrder::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );
    }

    @Transactional
    public boolean handlePaymentNotify(String orderNo, String tradeNo, String status, Map<String, String> allParams) {
        PortalPaymentOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getOrderNo, orderNo)
        );

        if (order == null) {
            log.warn("门户支付回调: 订单不存在, orderNo={}", orderNo);
            return false;
        }

        if (order.getStatus() != 0) {
            log.info("门户支付回调: 订单已处理, orderNo={}", orderNo);
            return true;
        }

        // 获取该应用的支付配置并验签
        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, order.getAppId())
                .eq(ApplicationEpayConfig::getEnabled, true)
        );
        if (config == null) {
            log.warn("门户支付回调: 应用支付配置不存在, appId={}", order.getAppId());
            return false;
        }

        if (!verifyReturnSign(orderNo, allParams)) {
            log.warn("门户支付回调: 签名验证失败, orderNo={}", orderNo);
            return false;
        }

        if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
            order.setStatus(1);
            order.setTradeNo(tradeNo);
            order.setPaidAt(LocalDateTime.now());
            order.setNotifyReceived(true);
            orderMapper.updateById(order);

            extendMembership(order);

            log.info("门户支付订单完成: orderNo={}, tradeNo={}", orderNo, tradeNo);
        }

        return true;
    }

    /**
     * 同步回调验签：根据订单号找到应用的支付配置，再验签
     */
    public boolean verifyReturnSign(String orderNo, Map<String, String> params) {
        PortalPaymentOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("同步回调验签: 订单不存在, orderNo={}", orderNo);
            return false;
        }

        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, order.getAppId())
                .eq(ApplicationEpayConfig::getEnabled, true)
        );
        if (config == null) {
            log.warn("同步回调验签: 应用支付配置不存在, appId={}", order.getAppId());
            return false;
        }

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

        String calculatedSign = calculateSign(sorted, config.getEpayKey());
        return receivedSign.equalsIgnoreCase(calculatedSign);
    }

    private void extendMembership(PortalPaymentOrder order) {
        AppUser appUser = appUserMapper.selectById(order.getAppUserId());
        if (appUser == null) return;

        LocalDateTime baseTime = appUser.getMemberExpiresAt() != null
            && appUser.getMemberExpiresAt().isAfter(LocalDateTime.now())
            ? appUser.getMemberExpiresAt()
            : LocalDateTime.now();

        if (order.getDurationDays() == null || order.getDurationDays() <= 0) {
            // 永久会员
            appUser.setMemberExpiresAt(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        } else {
            appUser.setMemberExpiresAt(baseTime.plusDays(order.getDurationDays()));
        }

        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
    }

    private String buildPayUrl(ApplicationEpayConfig config, PortalPaymentOrder order) {
        String money = String.format("%.2f", order.getAmountFen() / 100.0);

        // 回调和跳转地址从系统配置获取
        String notifyUrl = systemConfigService.getConfigValue("payment.portal.notify.url", "");
        String returnUrl = systemConfigService.getConfigValue("payment.portal.return.url", "");

        TreeMap<String, String> params = new TreeMap<>();
        params.put("pid", config.getEpayPid());
        params.put("type", "alipay");
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("out_trade_no", order.getOrderNo());
        params.put("name", order.getPlanName());
        params.put("money", money);
        params.put("sitename", "CipherGate");

        String sign = calculateSign(params, config.getEpayKey());
        params.put("sign", sign);
        params.put("sign_type", "MD5");

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) query.append("&");
            query.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        return config.getEpayUrl() + "/submit.php?" + query;
    }

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

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
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
        return "P" + LocalDateTime.now().format(ORDER_NO_FMT)
            + String.format("%04d", new Random().nextInt(10000));
    }
}
