package com.ayssu.ciphergate.handler;

import com.ayssu.ciphergate.config.RabbitMQConfig;
import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final PaymentOrderService paymentOrderService;

    /**
     * 监听死信队列，处理超时未支付的订单
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_DLQ)
    public void handleOrderTimeout(Map<String, Object> message) {
        String orderNo = (String) message.get("orderNo");
        if (orderNo == null) return;

        log.info("订单超时检查: orderNo={}", orderNo);

        boolean cancelled = paymentOrderService.lambdaUpdate()
                .eq(PaymentOrder::getOrderNo, orderNo).eq(PaymentOrder::getStatus, 0)
                .le(PaymentOrder::getCreatedAt, java.time.LocalDateTime.now().minusMinutes(5))
                .set(PaymentOrder::getStatus, 2).update();
        if (cancelled) log.info("订单已取消（超时未支付）: orderNo={}", orderNo);
    }
}
