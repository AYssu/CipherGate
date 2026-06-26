package com.ayssu.ciphergate.portal.scheduler;

import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
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

    private static final int TIMEOUT_MINUTES = 30;

    @Scheduled(fixedRate = 300000)
    public void closeExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        var expiredOrders = orderMapper.selectList(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .eq(PortalPaymentOrder::getStatus, 0)
                .lt(PortalPaymentOrder::getCreatedAt, expireTime)
        );

        if (expiredOrders.isEmpty()) return;

        for (PortalPaymentOrder order : expiredOrders) {
            order.setStatus(2);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("关闭超时未支付订单: orderNo={}, amount={}分", order.getOrderNo(), order.getAmountFen());
        }

        log.info("本次关闭超时订单数量: {}", expiredOrders.size());
    }
}
