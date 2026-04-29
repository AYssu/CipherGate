package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.constant.AccessEventTypes;
import com.ayssu.ciphergate.dto.DashboardOnlineDTO;
import com.ayssu.ciphergate.dto.DashboardOverviewDTO;
import com.ayssu.ciphergate.dto.DashboardTrendPointDTO;
import com.ayssu.ciphergate.dto.DashboardTodayStatsDTO;
import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.entity.ActivityLogEntity;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import com.ayssu.ciphergate.mapper.ActivityLogMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.thirdparty.ws.service.AppUserWsPresenceRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 仪表盘统计：默认仅统计「当前用户作为 owner」的应用下数据；后台 GitHub 登录仅管理员可查。
 */
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final LicenseKeyMapper licenseKeyMapper;
    private final AppUserMapper appUserMapper;
    private final AccessEventMapper accessEventMapper;
    private final ActivityLogMapper activityLogMapper;
    private final AppUserWsPresenceRegistry appUserWsPresenceRegistry;

    public DashboardTodayStatsDTO getTodayStats(List<Long> ownedAppIds, boolean includePlatformLogin) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime endExclusive = start.plusDays(1);

        DashboardTodayStatsDTO dto = new DashboardTodayStatsDTO();

        if (ownedAppIds == null || ownedAppIds.isEmpty()) {
            dto.setCardFirstActivatedToday(0);
            dto.setCardLoginToday(0);
            dto.setAppUserRegisteredToday(0);
            dto.setAppUserWsLoginToday(0);
        } else {
            dto.setCardFirstActivatedToday(licenseKeyMapper.selectCount(new LambdaQueryWrapper<LicenseKey>()
                    .in(LicenseKey::getAppId, ownedAppIds)
                    .isNotNull(LicenseKey::getFirstUsedAt)
                    .ge(LicenseKey::getFirstUsedAt, start)
                    .lt(LicenseKey::getFirstUsedAt, endExclusive)));

            dto.setCardLoginToday(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                    .in(AccessEvent::getEventType,
                            Arrays.asList(AccessEventTypes.CARD_LOGIN, AccessEventTypes.CARD_LOGIN_FREE))
                    .in(AccessEvent::getAppId, ownedAppIds)
                    .ge(AccessEvent::getCreatedAt, start)
                    .lt(AccessEvent::getCreatedAt, endExclusive)));

            dto.setAppUserRegisteredToday(appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                    .in(AppUser::getAppId, ownedAppIds)
                    .ge(AppUser::getCreatedAt, start)
                    .lt(AppUser::getCreatedAt, endExclusive)));

            dto.setAppUserWsLoginToday(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                    .eq(AccessEvent::getEventType, AccessEventTypes.APP_USER_WS_LOGIN)
                    .in(AccessEvent::getAppId, ownedAppIds)
                    .ge(AccessEvent::getCreatedAt, start)
                    .lt(AccessEvent::getCreatedAt, endExclusive)));
        }

        if (includePlatformLogin) {
            long n = activityLogMapper.selectCount(new LambdaQueryWrapper<ActivityLogEntity>()
                    .eq(ActivityLogEntity::getActionType, "LOGIN")
                    .eq(ActivityLogEntity::getActionTarget, "AUTHENTICATION")
                    .ge(ActivityLogEntity::getCreatedTime, start)
                    .lt(ActivityLogEntity::getCreatedTime, endExclusive));
            dto.setPlatformLoginToday(n);
        }

        return dto;
    }

    public DashboardOverviewDTO getOverview(List<Long> ownedAppIds) {
        DashboardOverviewDTO dto = new DashboardOverviewDTO();
        if (ownedAppIds == null || ownedAppIds.isEmpty()) {
            return dto;
        }
        dto.setAppCount(ownedAppIds.size());
        dto.setAppUserTotal(appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .in(AppUser::getAppId, ownedAppIds)
                .eq(AppUser::getDeleted, 0)));
        dto.setLicenseTotal(licenseKeyMapper.selectCount(new LambdaQueryWrapper<LicenseKey>()
                .in(LicenseKey::getAppId, ownedAppIds)
                .eq(LicenseKey::getDeleted, 0)));

        LocalDateTime start7d = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime endExclusive = LocalDate.now().plusDays(1).atStartOfDay();
        dto.setCardLogin7d(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                .in(AccessEvent::getEventType, Arrays.asList(AccessEventTypes.CARD_LOGIN, AccessEventTypes.CARD_LOGIN_FREE))
                .in(AccessEvent::getAppId, ownedAppIds)
                .ge(AccessEvent::getCreatedAt, start7d)
                .lt(AccessEvent::getCreatedAt, endExclusive)));
        dto.setAppUserWsLogin7d(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                .eq(AccessEvent::getEventType, AccessEventTypes.APP_USER_WS_LOGIN)
                .in(AccessEvent::getAppId, ownedAppIds)
                .ge(AccessEvent::getCreatedAt, start7d)
                .lt(AccessEvent::getCreatedAt, endExclusive)));
        return dto;
    }

    public DashboardOnlineDTO getOnlineStats(List<Long> ownedAppIds) {
        DashboardOnlineDTO dto = new DashboardOnlineDTO();
        if (ownedAppIds == null || ownedAppIds.isEmpty()) {
            return dto;
        }
        LocalDateTime onlineCutoff = LocalDateTime.now().minusMinutes(5);
        dto.setCardOnlineCount(licenseKeyMapper.selectCount(new LambdaQueryWrapper<LicenseKey>()
                .in(LicenseKey::getAppId, ownedAppIds)
                .eq(LicenseKey::getDeleted, 0)
                .isNotNull(LicenseKey::getLastUsedAt)
                .gt(LicenseKey::getLastUsedAt, onlineCutoff)));

        Collection<Long> onlineIds = appUserWsPresenceRegistry.listOnlineAppUserIds();
        if (onlineIds == null || onlineIds.isEmpty()) {
            dto.setAppUserOnlineCount(0);
        } else {
            dto.setAppUserOnlineCount(appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                    .in(AppUser::getId, onlineIds)
                    .in(AppUser::getAppId, ownedAppIds)
                    .eq(AppUser::getDeleted, 0)));
        }
        return dto;
    }

    public List<DashboardTrendPointDTO> getTrend7d(List<Long> ownedAppIds) {
        List<DashboardTrendPointDTO> out = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            DashboardTrendPointDTO p = new DashboardTrendPointDTO();
            p.setDate(day.toString());
            if (ownedAppIds == null || ownedAppIds.isEmpty()) {
                out.add(p);
                continue;
            }
            p.setAppUserRegistered(appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                    .in(AppUser::getAppId, ownedAppIds)
                    .eq(AppUser::getDeleted, 0)
                    .ge(AppUser::getCreatedAt, start)
                    .lt(AppUser::getCreatedAt, end)));
            p.setCardLogin(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                    .in(AccessEvent::getEventType, Arrays.asList(AccessEventTypes.CARD_LOGIN, AccessEventTypes.CARD_LOGIN_FREE))
                    .in(AccessEvent::getAppId, ownedAppIds)
                    .ge(AccessEvent::getCreatedAt, start)
                    .lt(AccessEvent::getCreatedAt, end)));
            p.setAppUserWsLogin(accessEventMapper.selectCount(new LambdaQueryWrapper<AccessEvent>()
                    .eq(AccessEvent::getEventType, AccessEventTypes.APP_USER_WS_LOGIN)
                    .in(AccessEvent::getAppId, ownedAppIds)
                    .ge(AccessEvent::getCreatedAt, start)
                    .lt(AccessEvent::getCreatedAt, end)));
            out.add(p);
        }
        return out;
    }
}
