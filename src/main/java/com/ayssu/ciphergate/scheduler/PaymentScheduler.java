package com.ayssu.ciphergate.scheduler;

import com.ayssu.ciphergate.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentOrderService paymentOrderService;

    /**
     * 启动时立即扫描一次，清理历史遗留的过期订单
     */
    @jakarta.annotation.PostConstruct
    public void onStartup() {
        log.info("启动时扫描过期订单...");
        paymentOrderService.cancelExpiredOrders();
    }

    /**
     * 每30分钟兜底扫描一次，防止 RabbitMQ 延迟消息丢失
     */
    @Scheduled(fixedRate = 1800000)
    public void cancelExpiredOrders() {
        paymentOrderService.cancelExpiredOrders();
    }
}
