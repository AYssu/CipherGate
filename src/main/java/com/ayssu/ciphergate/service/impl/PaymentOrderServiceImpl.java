package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.RabbitMQConfig;
import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.entity.QuotaProduct;
import com.ayssu.ciphergate.mapper.PaymentOrderMapper;
import com.ayssu.ciphergate.service.PaymentOrderService;
import com.ayssu.ciphergate.service.QuotaProductService;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder> implements PaymentOrderService {

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private QuotaProductService quotaProductService;

    @Autowired
    private UserMembershipService userMembershipService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public PaymentOrder createOrder(Long userId, Long productId, int quantity) {
        QuotaProduct product = quotaProductService.getById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }

        Long totalAmount = product.getPrice() * quantity;

        if ("MEMBERSHIP".equals(product.getProductType())) {
            boolean deducted = userMembershipService.deductBalance(userId, totalAmount, null, "购买会员：" + product.getProductName());
            if (!deducted) {
                throw new RuntimeException("余额不足");
            }

            PaymentOrder order = new PaymentOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setProductId(productId);
            order.setProductType(product.getProductType());
            order.setProductName(product.getProductName());
            order.setQuantity(quantity);
            order.setTotalAmount(totalAmount);
            order.setPayAmount(totalAmount);
            order.setStatus(1);
            order.setPaidAt(LocalDateTime.now());
            order.setAdminGranted(false);
            order.setCreatedAt(LocalDateTime.now());
            save(order);

            userMembershipService.upgradeLevel(userId, product.getQuotaValue(), null, "余额购买会员");
            return order;
        }

        boolean deducted = userMembershipService.deductBalance(userId, totalAmount, null, "购买额度：" + product.getProductName());
        if (!deducted) {
            throw new RuntimeException("余额不足");
        }

        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductType(product.getProductType());
        order.setProductName(product.getProductName());
        order.setQuantity(quantity);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setStatus(1);
        order.setPaidAt(LocalDateTime.now());
        order.setAdminGranted(false);
        order.setCreatedAt(LocalDateTime.now());
        save(order);

        grantQuotaByProduct(order.getUserId(), product, order.getQuantity());
        return order;
    }

    @Override
    @Transactional
    public PaymentOrder createPendingOrder(Long userId, String productName, Long amountFen, String orderNo, String productType) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(0L);
        order.setProductType(productType);
        order.setProductName(productName);
        order.setQuantity(1);
        order.setTotalAmount(amountFen);
        order.setPayAmount(0L);
        order.setStatus(0);
        order.setAdminGranted(false);
        order.setCreatedAt(LocalDateTime.now());
        save(order);

        // 发送延迟消息，5分钟后检查订单是否已支付
        Map<String, Object> message = new HashMap<>();
        message.put("orderNo", orderNo);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_TIMEOUT_EXCHANGE,
                RabbitMQConfig.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                message);

        return order;
    }

    @Override
    public PaymentOrder getByOrderNo(String orderNo) {
        return paymentOrderMapper.selectByOrderNo(orderNo);
    }

    @Override
    @Transactional
    public void handlePaymentSuccess(String orderNo, String tradeNo) {
        PaymentOrder order = getByOrderNo(orderNo);
        if (order == null || order.getStatus() != 0) {
            log.warn("订单不存在或状态异常: {}", orderNo);
            return;
        }

        order.setStatus(1);
        order.setTradeNo(tradeNo);
        order.setPaidAt(LocalDateTime.now());
        updateById(order);

        if ("RECHARGE".equals(order.getProductType())) {
            userMembershipService.grantBalance(order.getUserId(), order.getTotalAmount(), null, "在线充值");
        } else {
            QuotaProduct product = quotaProductService.getById(order.getProductId());
            if (product != null) {
                grantQuotaByProduct(order.getUserId(), product, order.getQuantity());
            }
        }

        Map<String, Object> message = new HashMap<>();
        message.put("orderNo", orderNo);
        message.put("userId", order.getUserId());
        rabbitTemplate.convertAndSend("payment.exchange", "payment.notify", message);

        log.info("支付成功处理完成: orderNo={}", orderNo);
    }

    @Override
    @Transactional
    public void handleAdminGrant(Long orderId, Long adminId) {
        PaymentOrder order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        order.setStatus(1);
        order.setPaidAt(LocalDateTime.now());
        order.setAdminGranted(true);
        order.setAdminOperatorId(adminId);
        updateById(order);

        QuotaProduct product = quotaProductService.getById(order.getProductId());
        if (product != null) {
            grantQuotaByProduct(order.getUserId(), product, order.getQuantity());
        }

        log.info("管理员手动发放: orderId={}, adminId={}", orderId, adminId);
    }

    @Override
    public void cancelExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(5);
        lambdaUpdate()
                .eq(PaymentOrder::getStatus, 0)
                .and(w -> w
                        .lt(PaymentOrder::getCreatedAt, expireTime)
                        .or()
                        .isNull(PaymentOrder::getCreatedAt))
                .set(PaymentOrder::getStatus, 2)
                .update();
    }

    @Override
    public Page<PaymentOrder> getUserOrders(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(PaymentOrder::getUserId, userId)
                .orderByDesc(PaymentOrder::getCreatedAt)
                .page(new Page<>(page, size));
    }

    @Override
    public Page<PaymentOrder> getAllOrders(int page, int size, Integer status) {
        var query = lambdaQuery();
        if (status != null) {
            query.eq(PaymentOrder::getStatus, status);
        }
        return query.orderByDesc(PaymentOrder::getCreatedAt).page(new Page<>(page, size));
    }

    private void grantQuotaByProduct(Long userId, QuotaProduct product, int quantity) {
        long totalQuota = product.getQuotaValue() * quantity;
        switch (product.getProductType()) {
            case "APP_QUOTA" -> {
                var membership = userMembershipService.getByUserId(userId);
                if (membership != null) {
                    membership.setAppUsed(membership.getAppUsed() - (int) totalQuota);
                    userMembershipService.updateById(membership);
                }
            }
            case "LICENSE_QUOTA" -> userMembershipService.consumeLicenseQuota(userId, -totalQuota);
            case "USER_REGISTER_QUOTA" -> userMembershipService.consumeUserRegisterQuota(userId, -(int) totalQuota);
            case "TRAFFIC_QUOTA" -> userMembershipService.consumeTrafficQuota(userId, -totalQuota);
            default -> log.warn("未知商品类型: {}", product.getProductType());
        }
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
