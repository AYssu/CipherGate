package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.ayssu.ciphergate.portal.service.PortalMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal/membership")
@Tag(name = "门户会员", description = "终端用户会员信息")
@RequiredArgsConstructor
public class PortalMembershipController {

    private final PortalMembershipService membershipService;
    private final AppUserMapper appUserMapper;

    @GetMapping("/info")
    @Operation(summary = "获取会员信息")
    public Result<Map<String, Object>> getInfo() {
        Long userId = getCurrentUserId();
        Long appId = getCurrentAppId();
        if (userId == null) return Result.error(401, "未登录");
        if (appId == null || appId == 0) {
            com.ayssu.ciphergate.entity.AppUser appUser = appUserMapper.selectById(userId);
            if (appUser != null) appId = appUser.getAppId();
        }
        if (userId == null || appId == null) return Result.error(401, "未登录");
        Map<String, Object> info = membershipService.getMembershipInfo(userId, appId);
        return Result.success(info);
    }

    @GetMapping("/plans")
    @Operation(summary = "获取定价方案")
    public Result<List<PortalPricingPlan>> getPlans() {
        Long userId = getCurrentUserId();
        Long appId = getCurrentAppId();
        if (userId == null) return Result.error(401, "未登录");
        // appId=0 说明是多应用选择的临时token，从用户记录中取真实appId
        if (appId == null || appId == 0) {
            com.ayssu.ciphergate.entity.AppUser appUser = appUserMapper.selectById(userId);
            if (appUser != null) appId = appUser.getAppId();
        }
        if (appId == null || appId == 0) return Result.error(400, "无法确定应用");
        List<PortalPricingPlan> plans = membershipService.getPricingPlans(appId);
        return Result.success(plans);
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
