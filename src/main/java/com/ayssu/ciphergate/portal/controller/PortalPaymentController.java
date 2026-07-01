package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.portal.dto.PortalOrderCreateRequest;
import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.service.PortalPaymentService;
import com.ayssu.ciphergate.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/portal/payment")
@Tag(name = "门户支付", description = "终端用户支付与订单")
@RequiredArgsConstructor
public class PortalPaymentController {

    private final PortalPaymentService paymentService;
    private final SystemConfigService systemConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建支付订单")
    public Result<Map<String, Object>> createOrder(@Valid @RequestBody PortalOrderCreateRequest request) {
        Long userId = getCurrentUserId();
        Long appId = getCurrentAppId();
        if (userId == null || appId == null) return Result.error(401, "未登录");
        try {
            PortalPaymentOrder order = paymentService.createOrder(userId, appId, request.getPlanId());
            return Result.success("订单创建成功", Map.of(
                "orderNo", order.getOrderNo(),
                "payUrl", order.getPayUrl() != null ? order.getPayUrl() : "",
                "amount", order.getAmountFen(),
                "planName", order.getPlanName() != null ? order.getPlanName() : ""
            ));
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @GetMapping("/orders")
    @Operation(summary = "我的订单")
    public Result<List<PortalPaymentOrder>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        List<PortalPaymentOrder> orders = paymentService.getOrders(userId, page, size);
        return Result.success(orders);
    }

    @RequestMapping(value = "/notify", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "支付异步回调")
    public Result<String> notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) params.put(key, values[0]);
        });
        try {
            String orderNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String status = params.get("trade_status");
            if (orderNo == null || tradeNo == null) {
                return Result.success("success");
            }
            boolean result = paymentService.handlePaymentNotify(orderNo, tradeNo, status, params);
            return result ? Result.success("success") : Result.error("订单不存在");
        } catch (Exception e) {
            log.error("门户支付回调处理失败", e);
            return Result.error("处理失败");
        }
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
        String successUrl = systemConfigService.getConfigValue("payment.portal.success.url", "/portal/orders");
        log.info("门户支付同步回调: orderNo={}, status={}", orderNo, tradeStatus);

        // 验签（需要从订单获取应用的key）
        if (!paymentService.verifyReturnSign(orderNo, params)) {
            log.warn("门户同步回调签名验证失败: orderNo={}", orderNo);
            return "<script>alert('签名验证失败');window.location.href='" + successUrl + "';</script>";
        }

        // 处理订单
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            paymentService.handlePaymentNotify(orderNo, params.get("trade_no"), tradeStatus, params);
        }

        return "<script>window.location.href='" + successUrl + "';</script>";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            return (Map<String, Object>) auth.getPrincipal();
        }
        return null;
    }

    private static Long getCurrentUserId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("id")).longValue() : null;
    }

    private static Long getCurrentAppId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("appId")).longValue() : null;
    }
}
