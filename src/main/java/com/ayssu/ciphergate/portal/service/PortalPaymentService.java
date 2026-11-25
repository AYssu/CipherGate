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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.ayssu.ciphergate.util.SkPaymentSignature;
import com.ayssu.ciphergate.util.SkPaymentGateway;
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

    @Transactional(noRollbackFor = PortalCheckoutPendingException.class)
    public PortalPaymentOrder createOrder(Long appUserId, Long appId, Long planId) {
        // 校验应用支付功能
        Application app = applicationMapper.selectById(appId);
        if (app == null || !Boolean.TRUE.equals(app.getPortalPaymentEnabled())) {
            throw new IllegalArgumentException("该应用未开启支付功能");
        }

        // 校验定价方案
        PortalPricingPlan plan = planMapper.selectForUpdate(planId);
        if (plan == null || !plan.getAppId().equals(appId) || !Boolean.TRUE.equals(plan.getEnabled())) {
            throw new IllegalArgumentException("定价方案不存在或已停用");
        }

        ApplicationEpayConfig epayConfig = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
                .eq(ApplicationEpayConfig::getEnabled, true));
        if (epayConfig == null) throw new IllegalArgumentException("该应用未配置支付网关");
        if (plan.getPriceFen() == null || plan.getPriceFen() <= 0) throw new IllegalArgumentException("方案价格无效");
        boolean limited = plan.getStockRemaining() != null;
        if (limited && planMapper.reserveStock(planId) != 1) {
            throw new IllegalArgumentException("该方案已售罄，请选择其他方案");
        }
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
        order.setStockReserved(limited);
        if (orderMapper.insert(order) != 1) throw new IllegalStateException("订单保存失败");

        // 通过 SK 网关创建订单并取得付款地址
        String payUrl;
        try {
            payUrl = buildPayUrl(epayConfig, order);
        } catch (RuntimeException e) {
            // An HTTP timeout does not prove the gateway failed to create its order.
            // Commit the snapshot and reservation, then reconcile through /pay/query.
            log.warn("门户付款地址暂不可用，保留订单等待核对: orderNo={}", order.getOrderNo());
            throw new PortalCheckoutPendingException();
        }
        order.setPayUrl(payUrl);
        if (orderMapper.updateById(order) != 1) throw new PortalCheckoutPendingException();

        log.info("创建门户支付订单: orderNo={}, appId={}, planId={}, amount={}",
            order.getOrderNo(), appId, planId, plan.getPriceFen());

        return order;
    }

    public List<PortalPaymentOrder> getOrders(Long appUserId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("页码必须大于0，每页数量须为1～100");
        }
        long offset = Math.multiplyExact((long) page - 1L, size);
        return orderMapper.selectList(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getAppUserId, appUserId)
                .orderByDesc(PortalPaymentOrder::getCreatedAt)
                .orderByDesc(PortalPaymentOrder::getId)
                .last("LIMIT " + size + " OFFSET " + offset)
        );
    }

    @Transactional
    public boolean handlePaymentNotify(String orderNo, String tradeNo, String status, Map<String, String> allParams) {
        return handleCallback(orderNo, tradeNo, status, allParams, "payment.notify");
    }

    @Transactional
    public boolean handlePaymentReturn(String orderNo, String tradeNo, String status, Map<String, String> allParams) {
        return handleCallback(orderNo, tradeNo, status, allParams, "payment.return");
    }

    private boolean handleCallback(String orderNo, String tradeNo, String status, Map<String, String> allParams, String purpose) {
        if (allParams == null || !hasRequiredNotifyFields(allParams)
                || !java.util.Objects.equals(orderNo, allParams.get("out_trade_no"))
                || !java.util.Objects.equals(tradeNo, allParams.get("trade_no"))
                || !java.util.Objects.equals(status, allParams.get("trade_status"))) {
            log.warn("门户支付回调: 必填字段或订单号不匹配, orderNo={}", orderNo);
            return false;
        }

        PortalPaymentOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("门户支付回调: 订单不存在, orderNo={}", orderNo);
            return false;
        }

        if (!verifySign(orderNo, allParams, purpose)) {
            log.warn("门户支付回调: 签名验证失败, orderNo={}", orderNo);
            return false;
        }

        if (!isSuccessStatus(status)) {
            return true;
        }

        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, order.getAppId())
                .eq(ApplicationEpayConfig::getEnabled, true)
        );
        if (config == null || !config.getEpayPid().equals(allParams.get("pid"))) {
            log.warn("门户支付回调: 支付商户配置不匹配, appId={}", order.getAppId());
            return false;
        }

        long callbackAmountFen;
        try {
            BigDecimal money = new BigDecimal(allParams.get("money"));
            if (money.scale() > 2 || money.signum() < 0) {
                return false;
            }
            callbackAmountFen = money.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            log.warn("门户支付回调: 金额格式错误, orderNo={}", orderNo);
            return false;
        }
        if (order.getAmountFen() == null || order.getAmountFen() != callbackAmountFen) {
            log.warn("门户支付回调: 金额不匹配, orderNo={}, expected={}, actual={}",
                orderNo, order.getAmountFen(), callbackAmountFen);
            return false;
        }

        return completeOrder(order, tradeNo);
    }

    /** All inventory transitions lock plan -> order -> user, matching creation and management. */
    private boolean completeOrder(PortalPaymentOrder snapshot, String tradeNo) {
        lockPlan(snapshot);
        PortalPaymentOrder order = orderMapper.selectForUpdate(snapshot.getId());
        if (order == null) return false;
        if (Integer.valueOf(1).equals(order.getStatus())) return java.util.Objects.equals(order.getTradeNo(), tradeNo);
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            log.warn("已关闭门户订单收到付款结果，需人工核对: orderNo={}", order.getOrderNo());
            return false;
        }
        int changed = orderMapper.update(null, new LambdaUpdateWrapper<PortalPaymentOrder>()
            .eq(PortalPaymentOrder::getId, order.getId()).eq(PortalPaymentOrder::getStatus, 0)
            .set(PortalPaymentOrder::getStatus, 1)
            .set(PortalPaymentOrder::getTradeNo, tradeNo)
            .set(PortalPaymentOrder::getPaidAt, LocalDateTime.now())
            .set(PortalPaymentOrder::getNotifyReceived, true)
            .set(PortalPaymentOrder::getStockReserved, false));
        if (changed != 1) throw new IllegalStateException("订单完成状态更新失败");
        extendMembership(order);
        log.info("门户支付订单完成: orderNo={}, tradeNo={}", order.getOrderNo(), tradeNo);
        return true;
    }

    private void lockPlan(PortalPaymentOrder order) {
        PortalPricingPlan plan = order.getPlanId() == null ? null : planMapper.selectForUpdate(order.getPlanId());
        if (Boolean.TRUE.equals(order.getStockReserved()) && plan == null) {
            throw new IllegalStateException("库存方案缺失，需人工核对");
        }
    }

    /** A local timeout alone must never free stock while a payable/paid gateway order exists. */
    @Transactional
    public void reconcileExpiredOrder(Long orderId) {
        PortalPaymentOrder snapshot = orderMapper.selectById(orderId);
        if (snapshot == null || !Integer.valueOf(0).equals(snapshot.getStatus())
                || snapshot.getCreatedAt() == null || snapshot.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(30))) return;
        ApplicationEpayConfig config = epayConfigMapper.selectOne(new LambdaQueryWrapper<ApplicationEpayConfig>()
            .eq(ApplicationEpayConfig::getAppId, snapshot.getAppId()).eq(ApplicationEpayConfig::getEnabled, true));
        if (config == null) return; // Retain reservations until an operator restores the gateway configuration.
        SkPaymentGateway.OrderState remote = SkPaymentGateway.queryOrder(
            config.getEpayUrl(), config.getEpayPid(), snapshot.getOrderNo(), config.getEpayKey());
        if (remote != null && (snapshot.getAmountFen() == null || remote.amountFen() != snapshot.getAmountFen())) {
            throw new IllegalStateException("支付订单核对金额不一致");
        }
        if (remote != null && "PAID".equals(remote.status())) {
            completeOrder(snapshot, remote.tradeNo());
            return;
        }
        if (remote != null && !java.util.Set.of("CLOSED", "FAILED", "REFUNDED").contains(remote.status())) return;
        lockPlan(snapshot);
        PortalPaymentOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null || !Integer.valueOf(0).equals(order.getStatus())) return;
        int changed = orderMapper.update(null, new LambdaUpdateWrapper<PortalPaymentOrder>()
            .eq(PortalPaymentOrder::getId, orderId).eq(PortalPaymentOrder::getStatus, 0)
            .set(PortalPaymentOrder::getStatus, 2).set(PortalPaymentOrder::getStockReserved, false)
            .set(PortalPaymentOrder::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("订单关闭失败");
        if (Boolean.TRUE.equals(order.getStockReserved()) && planMapper.releaseStock(order.getPlanId()) != 1) {
            throw new IllegalStateException("库存释放失败");
        }
    }

    private boolean hasRequiredNotifyFields(Map<String, String> params) {
        return List.of("money", "name", "out_trade_no", "pid", "trade_no", "trade_status", "type", "sign")
            .stream()
            .allMatch(field -> params.get(field) != null && !params.get(field).isBlank());
    }

    private static boolean isSuccessStatus(String status) {
        return "TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status) || "FINISHED".equals(status);
    }

    /**
     * 同步回调验签：根据订单号找到应用的支付配置，再验签
     */
    public boolean verifyReturnSign(String orderNo, Map<String, String> params) {
        return verifySign(orderNo, params, "payment.return");
    }

    private boolean verifySign(String orderNo, Map<String, String> params, String purpose) {
        if (params == null || orderNo == null || !orderNo.equals(params.get("out_trade_no"))) return false;
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
        if (config == null || !hasRequiredNotifyFields(params)
            || !config.getEpayPid().equals(params.get("pid"))) {
            log.warn("同步回调验签: 支付配置或必填字段不匹配, appId={}", order.getAppId());
            return false;
        }

        return SkPaymentSignature.verify(params, config.getEpayKey(), purpose, java.time.Instant.now().getEpochSecond());
    }

    private void extendMembership(PortalPaymentOrder order) {
        AppUser appUser = appUserMapper.selectForUpdate(order.getAppUserId());
        if (appUser == null) throw new IllegalStateException("付款用户不存在，需人工核对");

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
        if (appUserMapper.updateById(appUser) != 1) throw new IllegalStateException("会员权益更新失败");
    }

    private String buildPayUrl(ApplicationEpayConfig config, PortalPaymentOrder order) {
        String money = BigDecimal.valueOf(order.getAmountFen(), 2).toPlainString();

        // 回调和跳转地址从系统配置获取
        String notifyUrl = systemConfigService.getConfigValue("payment.portal.notify.url", "");
        String returnUrl = systemConfigService.getConfigValue("payment.portal.return.url", "");

        TreeMap<String, String> params = new TreeMap<>();
        params.put("pid", config.getEpayPid());
        params.put("pay_type", "alipay");
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("out_trade_no", order.getOrderNo());
        params.put("name", order.getPlanName());
        params.put("money", money);
        params.put("sitename", "CipherGate");

        return SkPaymentGateway.createCheckout(config.getEpayUrl(), params, config.getEpayKey());
    }

    private String generateOrderNo() {
        return "P" + LocalDateTime.now().format(ORDER_NO_FMT)
            + String.format("%04d", new Random().nextInt(10000));
    }
}
