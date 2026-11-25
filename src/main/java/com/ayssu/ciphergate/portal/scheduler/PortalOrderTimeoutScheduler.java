package com.ayssu.ciphergate.portal.scheduler;

import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
import com.ayssu.ciphergate.portal.service.PortalPaymentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortalOrderTimeoutScheduler {

    private final PortalPaymentOrderMapper orderMapper;
    private final PortalPaymentService paymentService;

    private static final int TIMEOUT_MINUTES = 30;

    @Scheduled(fixedRate = 300000)
    public void closeExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        var orders = orderMapper.selectList(new LambdaQueryWrapper<PortalPaymentOrder>()
            .eq(PortalPaymentOrder::getStatus, 0).lt(PortalPaymentOrder::getCreatedAt, expireTime)
            .orderByAsc(PortalPaymentOrder::getUpdatedAt).last("LIMIT 100"));
        for (PortalPaymentOrder order : orders) {
            try {
                paymentService.reconcileExpiredOrder(order.getId());
            } catch (RuntimeException e) {
                log.warn("门户超时订单暂无法核对，保留订单及库存: orderNo={}", order.getOrderNo());
            } finally {
                // Rotate inconclusive orders, so the oldest failing gateway cannot starve later reservations.
                orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PortalPaymentOrder>()
                    .eq(PortalPaymentOrder::getId, order.getId()).eq(PortalPaymentOrder::getStatus, 0)
                    .set(PortalPaymentOrder::getUpdatedAt, LocalDateTime.now()));
            }
        }
    }
}
