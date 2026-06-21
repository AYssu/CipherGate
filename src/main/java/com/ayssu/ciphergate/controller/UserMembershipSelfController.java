package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.BalanceTransaction;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.BalanceTransactionService;
import com.ayssu.ciphergate.service.UserMembershipService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user/membership")
@RequiredArgsConstructor
@Tag(name = "我的会员", description = "用户端会员信息")
public class UserMembershipSelfController {

    private final UserMembershipService userMembershipService;
    private final BalanceTransactionService balanceTransactionService;

    @GetMapping("/info")
    @Operation(summary = "获取我的会员信息")
    public Result<UserMembership> getMyMembership(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        UserMembership membership = userMembershipService.getByUserId(user.getId());
        if (membership == null) {
            userMembershipService.initMembershipForUser(user.getId());
            membership = userMembershipService.getByUserId(user.getId());
        }
        return Result.success(membership);
    }

    @GetMapping("/balance")
    @Operation(summary = "获取我的余额")
    public Result<Long> getMyBalance(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        UserMembership membership = userMembershipService.getByUserId(user.getId());
        return Result.success(membership != null ? membership.getBalance() : 0L);
    }

    @GetMapping("/transactions")
    @Operation(summary = "余额流水记录")
    public Result<Page<BalanceTransaction>> getTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(balanceTransactionService.getUserTransactions(user.getId(), page, size));
    }
}
