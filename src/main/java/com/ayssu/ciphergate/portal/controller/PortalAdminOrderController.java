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
import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.util.SecurityUtils;

@RestController
@RequestMapping("/api/portal/admin")
@Tag(name = "门户管理", description = "超级管理员查看终端用户订单")
@RequiredArgsConstructor
public class PortalAdminOrderController {

    private final PortalPaymentOrderMapper orderMapper;
    private final SecurityUtils securityUtils;

    @GetMapping("/orders")
    @RequirePermission("PAYMENT_ORDER_ADMIN_LIST")
    @Operation(summary = "所有终端用户订单")
    public Result<List<PortalPaymentOrder>> getAllOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var operator = AuthUtils.getCurrentUser();
        if (operator == null || !securityUtils.isSuperAdmin(operator.getId())) {
            throw new SecurityException("仅超级管理员可查看所有终端用户订单");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 必须大于零，size 必须在 1 到 100 之间");
        }
        List<PortalPaymentOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<PortalPaymentOrder>()
                .orderByDesc(PortalPaymentOrder::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + ((long) page - 1) * size)
        );
        return Result.success(orders);
    }
}
