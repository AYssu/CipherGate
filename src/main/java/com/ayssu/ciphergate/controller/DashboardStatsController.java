package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.DashboardTodayStatsDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.DashboardStatsService;
import com.ayssu.ciphergate.service.UserService;
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
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.unauthorized("未登录");
        }
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String githubId = oauth2User.getAttribute("id").toString();
        User user = userService.getUserByGithubId(githubId);
        if (user == null) {
            return Result.unauthorized("用户不存在");
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
}
