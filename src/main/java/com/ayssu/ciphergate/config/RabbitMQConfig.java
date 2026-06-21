package com.ayssu.ciphergate.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // ==================== 支付回调队列 ====================
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_QUEUE = "payment.notify.queue";
    public static final String PAYMENT_ROUTING_KEY = "payment.notify";

    // ==================== 订单超时延迟队列 ====================
    public static final String ORDER_TIMEOUT_EXCHANGE = "order.timeout.exchange";
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_DLX_EXCHANGE = "order.timeout.dlx.exchange";
    public static final String ORDER_TIMEOUT_DLQ = "order.timeout.dlq";
    public static final String ORDER_TIMEOUT_DLQ_ROUTING_KEY = "order.timeout.dlx";

    /** 订单超时时间：5分钟（毫秒） */
    public static final long ORDER_TIMEOUT_MS = 5 * 60 * 1000;

    // ==================== 支付回调 ====================

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE).build();
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(PAYMENT_ROUTING_KEY);
    }

    // ==================== 订单超时延迟队列 ====================

    /** 死信交换机 */
    @Bean
    public DirectExchange orderTimeoutDlxExchange() {
        return new DirectExchange(ORDER_TIMEOUT_DLX_EXCHANGE);
    }

    /** 死信队列（订单超时处理） */
    @Bean
    public Queue orderTimeoutDlq() {
        return QueueBuilder.durable(ORDER_TIMEOUT_DLQ).build();
    }

    @Bean
    public Binding orderTimeoutDlqBinding(Queue orderTimeoutDlq, DirectExchange orderTimeoutDlxExchange) {
        return BindingBuilder.bind(orderTimeoutDlq).to(orderTimeoutDlxExchange).with(ORDER_TIMEOUT_DLQ_ROUTING_KEY);
    }

    /** 延迟队列（消息在此队列等待30分钟后投递到死信队列） */
    @Bean
    public Queue orderTimeoutDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_TIMEOUT_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_TIMEOUT_DLQ_ROUTING_KEY);
        args.put("x-message-ttl", ORDER_TIMEOUT_MS);
        return QueueBuilder.durable(ORDER_TIMEOUT_DELAY_QUEUE).withArguments(args).build();
    }

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDER_TIMEOUT_EXCHANGE);
    }

    @Bean
    public Binding orderTimeoutDelayBinding(Queue orderTimeoutDelayQueue, DirectExchange orderTimeoutExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderTimeoutExchange).with(ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    // ==================== 通用配置 ====================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
