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
        if (quantity < 1 || quantity > 10000) throw new IllegalArgumentException("购买数量必须在1到10000之间");
        QuotaProduct product = quotaProductService.getById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }

        if (product.getPrice() == null || product.getPrice() <= 0) throw new IllegalArgumentException("商品价格必须大于0");
        if ("MEMBERSHIP".equals(product.getProductType()) && quantity != 1) {
            throw new IllegalArgumentException("会员商品每次只能购买1份");
        }
        Long totalAmount = Math.multiplyExact(product.getPrice(), quantity);

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
            if (!save(order)) throw new IllegalStateException("订单保存失败");

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
        if (!save(order)) throw new IllegalStateException("订单保存失败");

        grantQuotaByProduct(order.getUserId(), product, order.getQuantity());
        return order;
    }

    @Override
    @Transactional
    public PaymentOrder createPendingOrder(Long userId, String productName, Long amountFen, String orderNo, String productType) {
        if (amountFen == null || amountFen <= 0) throw new IllegalArgumentException("订单金额必须大于0");
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
        if (!save(order)) throw new IllegalStateException("订单保存失败");

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
        if (order == null) {
            throw new IllegalArgumentException("支付订单不存在");
        }

        boolean claimed = lambdaUpdate()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getStatus, 0)
                .set(PaymentOrder::getStatus, 1)
                .set(PaymentOrder::getTradeNo, tradeNo)
                .set(PaymentOrder::getPaidAt, LocalDateTime.now())
                .update();
        if (!claimed) {
            log.info("支付回调重复到达，忽略: orderNo={}", orderNo);
            return;
        }

        if ("RECHARGE".equals(order.getProductType())) {
            userMembershipService.grantBalance(order.getUserId(), order.getTotalAmount(), null, "在线充值");
        } else {
            QuotaProduct product = quotaProductService.getById(order.getProductId());
            if (product == null) throw new IllegalStateException("订单商品不存在，无法发放");
            if ("MEMBERSHIP".equals(product.getProductType())) {
                userMembershipService.upgradeLevel(order.getUserId(), product.getQuotaValue(), null, "在线购买会员");
            } else {
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

        boolean claimed = lambdaUpdate().eq(PaymentOrder::getId, orderId)
                .eq(PaymentOrder::getStatus, 0)
                .set(PaymentOrder::getStatus, 1).set(PaymentOrder::getPaidAt, LocalDateTime.now())
                .set(PaymentOrder::getAdminGranted, true).set(PaymentOrder::getAdminOperatorId, adminId).update();
        if (!claimed) throw new IllegalStateException("订单已处理，不能重复发放");

        if ("RECHARGE".equals(order.getProductType())) {
            userMembershipService.grantBalance(order.getUserId(), order.getTotalAmount(), adminId, "管理员确认充值");
        } else {
            QuotaProduct product = quotaProductService.getById(order.getProductId());
            if (product == null) throw new IllegalStateException("订单商品不存在，无法发放");
            if ("MEMBERSHIP".equals(product.getProductType())) {
                userMembershipService.upgradeLevel(order.getUserId(), product.getQuotaValue(), adminId, "管理员确认会员订单");
            } else {
                grantQuotaByProduct(order.getUserId(), product, order.getQuantity());
            }
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
                .page(com.ayssu.ciphergate.util.IdPageQuery.page(page, size));
    }

    @Override
    public Page<PaymentOrder> getAllOrders(int page, int size, Integer status) {
        var query = lambdaQuery();
        if (status != null) {
            query.eq(PaymentOrder::getStatus, status);
        }
        return query.orderByDesc(PaymentOrder::getCreatedAt).page(com.ayssu.ciphergate.util.IdPageQuery.page(page, size));
    }

    private void grantQuotaByProduct(Long userId, QuotaProduct product, int quantity) {
        long totalQuota = Math.multiplyExact(product.getQuotaValue(), quantity);
        if (totalQuota <= 0) throw new IllegalArgumentException("商品额度必须大于0");
        var membership = userMembershipService.getByUserIdForUpdate(userId);
        if (membership == null) throw new IllegalStateException("用户会员信息不存在，无法发放");

        switch (product.getProductType()) {
            case "APP_QUOTA" -> membership.setExtraAppQuota(Math.addExact(membership.getExtraAppQuota() != null ? membership.getExtraAppQuota() : 0, totalQuota));
            case "LICENSE_QUOTA" -> membership.setExtraLicenseQuota(Math.addExact(membership.getExtraLicenseQuota() != null ? membership.getExtraLicenseQuota() : 0, totalQuota));
            case "USER_REGISTER_QUOTA" -> membership.setExtraUserRegisterQuota(Math.addExact(membership.getExtraUserRegisterQuota() != null ? membership.getExtraUserRegisterQuota() : 0, totalQuota));
            case "TRAFFIC_QUOTA" -> membership.setExtraTrafficQuota(Math.addExact(membership.getExtraTrafficQuota() != null ? membership.getExtraTrafficQuota() : 0, totalQuota));
            default -> throw new IllegalArgumentException("不支持的商品类型");
        }
        if (!userMembershipService.updateById(membership)) throw new IllegalStateException("额度发放失败");
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
