package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.portal.dto.PortalDashboardStats;
import com.ayssu.ciphergate.portal.entity.PortalLoginLog;
import com.ayssu.ciphergate.portal.mapper.PortalLoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortalDashboardService {

    private final PortalLoginLogMapper loginLogMapper;
    private final AppUserMapper appUserMapper;
    private final AppUserBindingMapper appUserBindingMapper;

    public PortalDashboardStats getStats(Long appUserId, Long appId) {
        AppUser appUser = appUserMapper.selectById(appUserId);

        // 门户登录次数
        Long portalLoginCount = loginLogMapper.selectCount(
            new LambdaQueryWrapper<PortalLoginLog>()
                .eq(PortalLoginLog::getAppUserId, appUserId)
                .eq(PortalLoginLog::getStatus, "SUCCESS")
        );

        // 三方应用登录次数（app_user.login_count）
        int appLoginCount = appUser != null && appUser.getLoginCount() != null ? appUser.getLoginCount() : 0;

        // 绑定设备数
        Long boundDeviceCount = appUserBindingMapper.selectCount(
            new LambdaQueryWrapper<AppUserBinding>()
                .eq(AppUserBinding::getUserId, appUserId)
                .eq(AppUserBinding::getDeleted, 0)
        );

        // 最近30天登录趋势
        List<Map<String, Object>> loginTrend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Long count = loginLogMapper.selectCount(
                new LambdaQueryWrapper<PortalLoginLog>()
                    .eq(PortalLoginLog::getAppUserId, appUserId)
                    .eq(PortalLoginLog::getStatus, "SUCCESS")
                    .ge(PortalLoginLog::getCreatedAt, date.atStartOfDay())
                    .le(PortalLoginLog::getCreatedAt, date.atTime(LocalTime.MAX))
            );
            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toString());
            point.put("count", count);
            loginTrend.add(point);
        }

        // IP 归属分布
        List<PortalLoginLog> recentLogs = loginLogMapper.selectList(
            new LambdaQueryWrapper<PortalLoginLog>()
                .eq(PortalLoginLog::getAppUserId, appUserId)
                .eq(PortalLoginLog::getStatus, "SUCCESS")
                .orderByDesc(PortalLoginLog::getCreatedAt)
                .last("LIMIT 100")
        );

        Map<String, Long> ipCountMap = recentLogs.stream()
            .filter(l -> l.getIpRegion() != null)
            .collect(Collectors.groupingBy(PortalLoginLog::getIpRegion, Collectors.counting()));

        List<Map<String, Object>> ipDistribution = ipCountMap.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(e -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", e.getKey());
                item.put("value", e.getValue());
                return item;
            })
            .collect(Collectors.toList());

        return PortalDashboardStats.builder()
            .totalLoginCount(portalLoginCount.intValue() + appLoginCount)
            .portalLoginCount(portalLoginCount.intValue())
            .appLoginCount(appLoginCount)
            .boundDeviceCount(boundDeviceCount.intValue())
            .lastLoginIp(appUser != null ? appUser.getLastLoginIp() : null)
            .lastLoginIpRegion(appUser != null ? appUser.getLastLoginIpRegion() : null)
            .lastLoginAt(appUser != null && appUser.getLastLoginAt() != null ? appUser.getLastLoginAt().toString() : null)
            .loginTrend(loginTrend)
            .onlineTrend(new ArrayList<>())
            .ipDistribution(ipDistribution)
            .build();
    }

    public List<PortalLoginLog> getLoginHistory(Long appUserId, int page, int size) {
        return loginLogMapper.selectList(
            new LambdaQueryWrapper<PortalLoginLog>()
                .eq(PortalLoginLog::getAppUserId, appUserId)
                .orderByDesc(PortalLoginLog::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size)
        );
    }

    public List<Map<String, Object>> getDevices(Long appUserId) {
        List<AppUserBinding> bindings = appUserBindingMapper.selectList(
            new LambdaQueryWrapper<AppUserBinding>()
                .eq(AppUserBinding::getUserId, appUserId)
                .eq(AppUserBinding::getDeleted, 0)
        );

        return bindings.stream().map(b -> {
            Map<String, Object> device = new HashMap<>();
            device.put("id", b.getId());
            device.put("deviceId", b.getDeviceId());
            device.put("createdAt", b.getCreatedAt());
            return device;
        }).collect(Collectors.toList());
    }
}
