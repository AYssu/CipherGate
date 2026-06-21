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

        PaymentOrder order = paymentOrderService.getByOrderNo(orderNo);
        if (order == null || order.getStatus() != 0) {
            log.info("订单已处理或不存在，跳过: orderNo={}, status={}", orderNo, order != null ? order.getStatus() : "null");
            return;
        }

        order.setStatus(2);
        paymentOrderService.updateById(order);
        log.info("订单已取消（超时未支付）: orderNo={}", orderNo);
    }
}
