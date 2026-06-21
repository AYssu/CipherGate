package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.service.UserMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/membership/users")
@RequiredArgsConstructor
@Tag(name = "用户会员管理", description = "超级管理员管理用户会员信息")
public class UserMembershipAdminController {

    private final UserMembershipService userMembershipService;

    @GetMapping("/{userId}")
    @RequirePermission("USER_MEMBERSHIP_DETAIL")
    @Operation(summary = "获取用户会员详情")
    public Result<UserMembership> getUserMembership(@PathVariable Long userId) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            return Result.error("用户会员信息不存在");
        }
        return Result.success(membership);
    }

    @PutMapping("/{userId}")
    @RequirePermission("USER_MEMBERSHIP_UPDATE")
    @Operation(summary = "管理员修改用户会员等级")
    public Result<String> updateUserMembership(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body,
            jakarta.servlet.http.HttpSession session) {
        Long operatorId = getOperatorId(session);
        Long toLevelId = Long.valueOf(body.get("levelId").toString());
        String remark = (String) body.getOrDefault("remark", "管理员手动调整");
        userMembershipService.upgradeLevel(userId, toLevelId, operatorId, remark);
        return Result.success("更新成功");
    }

    @PostMapping("/{userId}/grant-balance")
    @RequirePermission("USER_MEMBERSHIP_GRANT_BALANCE")
    @Operation(summary = "管理员为用户充值余额")
    public Result<String> grantBalance(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body,
            jakarta.servlet.http.HttpSession session) {
        Long operatorId = getOperatorId(session);
        Long amount = Long.valueOf(body.get("amount").toString());
        String description = (String) body.getOrDefault("description", "管理员充值");
        userMembershipService.grantBalance(userId, amount, operatorId, description);
        return Result.success("充值成功");
    }

    private Long getOperatorId(jakarta.servlet.http.HttpSession session) {
        com.ayssu.ciphergate.entity.User user = (com.ayssu.ciphergate.entity.User) session.getAttribute("user");
        return user != null ? user.getId() : null;
    }
}
