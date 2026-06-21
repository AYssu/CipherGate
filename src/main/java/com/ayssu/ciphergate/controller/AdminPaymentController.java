package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.PaymentOrder;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.PaymentOrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/payment")
@RequiredArgsConstructor
@Tag(name = "管理员支付管理", description = "超级管理员订单管理")
public class AdminPaymentController {

    private final PaymentOrderService paymentOrderService;

    @GetMapping("/orders")
    @RequirePermission("PAYMENT_ORDER_ADMIN_LIST")
    @Operation(summary = "管理员查看所有订单")
    public Result<Page<PaymentOrder>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        return Result.success(paymentOrderService.getAllOrders(page, size, status));
    }

    @PostMapping("/grant")
    @RequirePermission("PAYMENT_ORDER_ADMIN_GRANT")
    @Operation(summary = "管理员手动发放")
    public Result<String> adminGrant(
            @RequestParam Long orderId,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        try {
            paymentOrderService.handleAdminGrant(orderId, user.getId());
            return Result.success("发放成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
