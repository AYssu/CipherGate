package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.config.EpayConfig;
import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.entity.QuotaProduct;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.EpayService;
import com.ayssu.ciphergate.service.PaymentOrderService;
import com.ayssu.ciphergate.service.QuotaProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "支付管理", description = "用户端支付和订单接口")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final EpayService epayService;
    private final EpayConfig epayConfig;
    private final QuotaProductService quotaProductService;

    @PostMapping("/create")
    @Operation(summary = "余额购买额度")
    public Result<Map<String, Object>> createOrder(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        try {
            PaymentOrder order = paymentOrderService.createOrder(user.getId(), productId, quantity);
            Map<String, Object> data = new HashMap<>();
            data.put("order", order);
            data.put("payUrl", null);
            return Result.success("购买成功", data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/recharge")
    @Operation(summary = "充值余额（易支付）")
    public Result<Map<String, Object>> recharge(
            @RequestParam Long amount,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        try {
            String orderNo = "RB" + System.currentTimeMillis();
            paymentOrderService.createPendingOrder(user.getId(), "余额充值", amount, orderNo, "RECHARGE");
            String payUrl = epayService.createEpayOrder(user.getId(), "余额充值", amount, orderNo);
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", orderNo);
            data.put("payUrl", payUrl);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/orders")
    @Operation(summary = "我的订单列表")
    public Result<Page<PaymentOrder>> getMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(paymentOrderService.getUserOrders(user.getId(), page, size));
    }

    @GetMapping("/orders/{orderNo}")
    @Operation(summary = "订单详情")
    public Result<PaymentOrder> getOrderDetail(@PathVariable String orderNo) {
        PaymentOrder order = paymentOrderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("订单不存在");
        }
        return Result.success(order);
    }

    @PostMapping("/notify")
    @Operation(summary = "易支付异步回调")
    public String paymentNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) params.put(key, values[0]);
        });

        log.info("易支付回调: {}", params);

        if (!epayService.verifyNotifySign(params)) {
            log.warn("回调签名验证失败");
            return "sign error";
        }

        epayService.handleNotify(params);
        return "success";
    }

    @GetMapping("/return")
    @Operation(summary = "支付同步回调")
    public String paymentReturn(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) params.put(key, values[0]);
        });

        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String redirectUrl = epayConfig.getSuccessRedirectUrl();
        log.info("支付同步回调: orderNo={}, status={}", orderNo, tradeStatus);

        if (!epayService.verifyNotifySign(params)) {
            log.warn("同步回调签名验证失败: orderNo={}", orderNo);
            return "<script>alert('签名验证失败');window.location.href='" + redirectUrl + "';</script>";
        }

        if ("TRADE_SUCCESS".equals(tradeStatus) || "FINISHED".equals(tradeStatus)) {
            paymentOrderService.handlePaymentSuccess(orderNo, params.get("trade_no"));
        }

        return "<script>alert('支付成功');window.location.href='" + redirectUrl + "';</script>";
    }
}
