package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portal/admin")
@Tag(name = "门户管理", description = "超级管理员查看终端用户订单")
@RequiredArgsConstructor
public class PortalAdminOrderController {

    private final PortalPaymentOrderMapper orderMapper;

    @GetMapping("/orders")
    @RequirePermission("PAYMENT_ORDER_ADMIN_LIST")
    @Operation(summary = "所有终端用户订单")
    public Result<List<PortalPaymentOrder>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PortalPaymentOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .orderByDesc(PortalPaymentOrder::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );
        return Result.success(orders);
    }
}
