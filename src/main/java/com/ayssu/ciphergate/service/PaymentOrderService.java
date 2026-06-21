package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.PaymentOrder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PaymentOrderService extends IService<PaymentOrder> {

    PaymentOrder createOrder(Long userId, Long productId, int quantity);

    PaymentOrder createPendingOrder(Long userId, String productName, Long amountFen, String orderNo, String productType);

    PaymentOrder getByOrderNo(String orderNo);

    void handlePaymentSuccess(String orderNo, String tradeNo);

    void handleAdminGrant(Long orderId, Long adminId);

    void cancelExpiredOrders();

    Page<PaymentOrder> getUserOrders(Long userId, int page, int size);

    Page<PaymentOrder> getAllOrders(int page, int size, Integer status);
}
