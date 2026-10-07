package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.DashboardOnlineDTO;
import com.ayssu.ciphergate.dto.DashboardAccessStatsDTO;
import com.ayssu.ciphergate.dto.DashboardAccessRecordDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ayssu.ciphergate.dto.DashboardOverviewDTO;
import com.ayssu.ciphergate.dto.DashboardTrendPointDTO;
import com.ayssu.ciphergate.dto.DashboardTodayStatsDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.DashboardStatsService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "仪表盘统计", description = "今日业务指标聚合")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardStatsController {

    private final DashboardStatsService dashboardStatsService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final ApplicationMapper applicationMapper;

    @GetMapping("/stats/today")
    @Operation(summary = "今日统计",
            description = "仅统计当前登录用户作为 owner 的应用；卡密/终端用户相关指标均按应用过滤。"
                    + "「今日后台登录」仅 ADMIN / SUPER_ADMIN 返回。")
    public Result<DashboardTodayStatsDTO> todayStats(Authentication authentication) {
        User user = AuthUtils.getCurrentUser();
        if (user == null && authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof OAuth2User oauth2User) {
                String githubId = oauth2User.getAttribute("id").toString();
                user = userService.getUserByGithubId(githubId);
            }
        }
        if (user == null) {
            return Result.unauthorized("未登录");
        }

        List<Long> ownedAppIds = applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                        .eq(Application::getOwnerId, user.getId())
                        .select(Application::getId))
                .stream()
                .map(Application::getId)
                .toList();

        boolean includePlatformLogin = securityUtils.isAdmin(user.getId());
        return Result.success(dashboardStatsService.getTodayStats(ownedAppIds, includePlatformLogin));
    }

    @GetMapping("/overview")
    @Operation(summary = "仪表盘总览")
    public Result<DashboardOverviewDTO> overview(Authentication authentication) {
        List<Long> ownedAppIds = getOwnedAppIds(authentication);
        return Result.success(dashboardStatsService.getOverview(ownedAppIds));
    }

    @GetMapping("/online")
    @Operation(summary = "近期活跃卡密与在线终端用户统计")
    public Result<DashboardOnlineDTO> online(Authentication authentication) {
        List<Long> ownedAppIds = getOwnedAppIds(authentication);
        return Result.success(dashboardStatsService.getOnlineStats(ownedAppIds));
    }

    @GetMapping("/trend/7d")
    @Operation(summary = "近7天趋势")
    public Result<List<DashboardTrendPointDTO>> trend7d(Authentication authentication) {
        List<Long> ownedAppIds = getOwnedAppIds(authentication);
        return Result.success(dashboardStatsService.getTrend7d(ownedAppIds));
    }

    @GetMapping("/access/stats")
    @Operation(summary = "登录活跃统计", description = "仅统计自己拥有的应用。成功登录即可记录，无需心跳；不表示实时在线。")
    public Result<DashboardAccessStatsDTO> accessStats(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        return Result.success(dashboardStatsService.getAccessStats(getOwnedAppIds(user)));
    }

    @GetMapping("/access/recent")
    @Operation(summary = "近7天成功登录记录", description = "按应用 owner 隔离；只返回统计身份与设备摘要，不返回卡密明文和 token。")
    public Result<Page<DashboardAccessRecordDTO>> recentAccess(Authentication authentication,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        User user = resolveCurrentUser(authentication);
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        return Result.success(dashboardStatsService.getRecentAccess(getOwnedAppIds(user), page, size));
    }

    private List<Long> getOwnedAppIds(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        return user == null ? List.of() : getOwnedAppIds(user);
    }

    private User resolveCurrentUser(Authentication authentication) {
        // 优先用 AuthUtils 获取用户（兼容密码登录和 OAuth2）
        User user = AuthUtils.getCurrentUser();
        if (user == null && authentication != null && authentication.isAuthenticated()) {
            // OAuth2 兜底
            Object principal = authentication.getPrincipal();
            if (principal instanceof OAuth2User oauth2User) {
                String githubId = oauth2User.getAttribute("id").toString();
                user = userService.getUserByGithubId(githubId);
            }
        }
        return user;
    }

    private List<Long> getOwnedAppIds(User user) {
        return applicationMapper.selectList(new LambdaQueryWrapper<Application>()
                        .eq(Application::getOwnerId, user.getId())
                        .select(Application::getId))
                .stream()
                .map(Application::getId)
                .toList();
    }
}
