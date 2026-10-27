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

        int closed = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PortalPaymentOrder>()
                        .eq(PortalPaymentOrder::getStatus, 0)
                        .lt(PortalPaymentOrder::getCreatedAt, expireTime)
                        .set(PortalPaymentOrder::getStatus, 2)
                        .set(PortalPaymentOrder::getUpdatedAt, LocalDateTime.now()));
        if (closed > 0) log.info("本次关闭超时订单数量: {}", closed);
    }
}
