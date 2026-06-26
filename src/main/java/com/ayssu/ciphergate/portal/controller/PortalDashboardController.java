package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.portal.dto.PortalDashboardStats;
import com.ayssu.ciphergate.portal.entity.PortalLoginLog;
import com.ayssu.ciphergate.portal.service.PortalDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal/dashboard")
@Tag(name = "门户控制台", description = "终端用户控制台数据")
@RequiredArgsConstructor
public class PortalDashboardController {

    private final PortalDashboardService dashboardService;
    private final AppUserMapper appUserMapper;

    @GetMapping("/stats")
    @Operation(summary = "获取控制台统计数据")
    public Result<PortalDashboardStats> getStats() {
        Long userId = getCurrentUserId();
        Long appId = getCurrentAppId();
        if (userId == null) return Result.error(401, "未登录");
        if (appId == null || appId == 0) {
            com.ayssu.ciphergate.entity.AppUser appUser = appUserMapper.selectById(userId);
            if (appUser != null) appId = appUser.getAppId();
        }
        if (appId == null || appId == 0) return Result.error(400, "无法确定应用");
        PortalDashboardStats stats = dashboardService.getStats(userId, appId);
        return Result.success(stats);
    }

    @GetMapping("/login-history")
    @Operation(summary = "登录历史")
    public Result<List<PortalLoginLog>> getLoginHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        List<PortalLoginLog> logs = dashboardService.getLoginHistory(userId, page, size);
        return Result.success(logs);
    }

    @GetMapping("/devices")
    @Operation(summary = "设备列表")
    public Result<List<Map<String, Object>>> getDevices() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        List<Map<String, Object>> devices = dashboardService.getDevices(userId);
        return Result.success(devices);
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
