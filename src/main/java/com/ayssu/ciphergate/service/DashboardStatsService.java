package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.constant.AccessEventTypes;
import com.ayssu.ciphergate.dto.DashboardTodayStatsDTO;
import com.ayssu.ciphergate.entity.AccessEvent;
import com.ayssu.ciphergate.entity.ActivityLogEntity;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.mapper.AccessEventMapper;
import com.ayssu.ciphergate.mapper.ActivityLogMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
                    .eq(AccessEvent::getEventType, AccessEventTypes.CARD_LOGIN)
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
}
