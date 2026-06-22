package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.MembershipLevel;
import com.ayssu.ciphergate.entity.UserMembership;
import com.ayssu.ciphergate.mapper.MembershipLevelMapper;
import com.ayssu.ciphergate.service.UserMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/membership/users")
@RequiredArgsConstructor
@Tag(name = "用户会员管理", description = "超级管理员管理用户会员信息")
public class UserMembershipAdminController {

    private final UserMembershipService userMembershipService;
    private final MembershipLevelMapper membershipLevelMapper;

    @GetMapping
    @RequirePermission("USER_MEMBERSHIP_DETAIL")
    @Operation(summary = "获取所有用户会员列表")
    public Result<List<Map<String, Object>>> listAll() {
        List<UserMembership> memberships = userMembershipService.list();
        List<MembershipLevel> levels = membershipLevelMapper.selectList(null);
        Map<Long, MembershipLevel> levelMap = levels.stream()
                .collect(Collectors.toMap(MembershipLevel::getId, l -> l));

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserMembership m : memberships) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("userId", m.getUserId());
            item.put("levelId", m.getLevelId());

            MembershipLevel level = levelMap.get(m.getLevelId());
            item.put("levelName", level != null ? level.getLevelName() : "未知");
            item.put("level", level != null ? level.getLevel() : 0);

            item.put("appUsed", m.getAppUsed());
            item.put("licenseUsed", m.getLicenseUsed());
            item.put("userRegisterUsed", m.getUserRegisterUsed());
            item.put("trafficUsed", m.getTrafficUsed());

            long appTotal = (level != null && level.getAppQuota() != null && level.getAppQuota() > 0) ? level.getAppQuota() : 0;
            long licenseTotal = (level != null && level.getLicenseQuota() != null && level.getLicenseQuota() > 0) ? level.getLicenseQuota() : 0;
            long userRegTotal = (level != null && level.getUserRegisterQuota() != null && level.getUserRegisterQuota() > 0) ? level.getUserRegisterQuota() : 0;
            long trafficTotal = (level != null && level.getTrafficQuota() != null && level.getTrafficQuota() > 0) ? level.getTrafficQuota() : 0;

            long extraApp = m.getExtraAppQuota() != null ? m.getExtraAppQuota() : 0;
            long extraLicense = m.getExtraLicenseQuota() != null ? m.getExtraLicenseQuota() : 0;
            long extraUserReg = m.getExtraUserRegisterQuota() != null ? m.getExtraUserRegisterQuota() : 0;
            long extraTraffic = m.getExtraTrafficQuota() != null ? m.getExtraTrafficQuota() : 0;

            item.put("extraAppQuota", extraApp);
            item.put("extraLicenseQuota", extraLicense);
            item.put("extraUserRegisterQuota", extraUserReg);
            item.put("extraTrafficQuota", extraTraffic);

            item.put("appTotal", appTotal + extraApp);
            item.put("licenseTotal", licenseTotal + extraLicense);
            item.put("userRegisterTotal", userRegTotal + extraUserReg);
            item.put("trafficTotal", trafficTotal + extraTraffic);

            item.put("balance", m.getBalance());
            item.put("inviteCode", m.getInviteCode());
            item.put("inviteCount", m.getInviteCount());
            item.put("invitedBy", m.getInvitedBy());
            item.put("consecutiveCheckinDays", m.getConsecutiveCheckinDays());
            item.put("totalCheckinDays", m.getTotalCheckinDays());
            item.put("lastCheckinDate", m.getLastCheckinDate());
            item.put("memberExpiresAt", m.getMemberExpiresAt());
            item.put("createdAt", m.getCreatedAt());
            result.add(item);
        }
        return Result.success(result);
    }

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

    @PutMapping("/{userId}/extra-quota")
    @RequirePermission("USER_MEMBERSHIP_UPDATE")
    @Operation(summary = "管理员修改用户额外额度")
    public Result<String> updateExtraQuota(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            return Result.error("用户会员信息不存在");
        }
        if (body.containsKey("extraAppQuota")) {
            membership.setExtraAppQuota(Long.valueOf(body.get("extraAppQuota").toString()));
        }
        if (body.containsKey("extraLicenseQuota")) {
            membership.setExtraLicenseQuota(Long.valueOf(body.get("extraLicenseQuota").toString()));
        }
        if (body.containsKey("extraUserRegisterQuota")) {
            membership.setExtraUserRegisterQuota(Long.valueOf(body.get("extraUserRegisterQuota").toString()));
        }
        if (body.containsKey("extraTrafficQuota")) {
            membership.setExtraTrafficQuota(Long.valueOf(body.get("extraTrafficQuota").toString()));
        }
        userMembershipService.updateById(membership);
        return Result.success("额度更新成功");
    }

    @PutMapping("/{userId}/expires")
    @RequirePermission("USER_MEMBERSHIP_UPDATE")
    @Operation(summary = "管理员设置会员到期时间")
    public Result<String> updateExpires(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        UserMembership membership = userMembershipService.getByUserId(userId);
        if (membership == null) {
            return Result.error("用户会员信息不存在");
        }
        String expiresStr = (String) body.get("memberExpiresAt");
        if (expiresStr == null || expiresStr.isEmpty()) {
            membership.setMemberExpiresAt(null);
        } else {
            membership.setMemberExpiresAt(java.time.LocalDateTime.parse(expiresStr));
        }
        userMembershipService.updateById(membership);
        return Result.success("到期时间更新成功");
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
