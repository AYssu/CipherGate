package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserTrial;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.AppUserTrialMapper;
import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.ayssu.ciphergate.portal.mapper.PortalPricingPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortalMembershipService {

    private final AppUserMapper appUserMapper;
    private final AppUserTrialMapper appUserTrialMapper;
    private final PortalPricingPlanMapper pricingPlanMapper;

    public Map<String, Object> getMembershipInfo(Long appUserId, Long appId) {
        AppUser appUser = appUserMapper.selectById(appUserId);
        if (appUser == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime memberExpiresAt = appUser.getMemberExpiresAt();

        // 计算会员状态
        String memberStatus;
        if (memberExpiresAt != null && memberExpiresAt.isAfter(now)) {
            memberStatus = "ACTIVE";
        } else if (memberExpiresAt != null) {
            memberStatus = "EXPIRED";
        } else {
            memberStatus = "NONE";
        }

        // 查询试用状态
        AppUserTrial trial = appUserTrialMapper.selectOne(
            new LambdaQueryWrapper<AppUserTrial>()
                .eq(AppUserTrial::getAppId, appId)
                .eq(AppUserTrial::getUserId, appUserId)
                .last("LIMIT 1")
        );

        boolean trialActive = trial != null
            && trial.getTrialExpiresAt() != null
            && trial.getTrialExpiresAt().isAfter(now);

        // 如果是试用期状态，覆盖 memberStatus
        if (trialActive && "NONE".equals(memberStatus)) {
            memberStatus = "TRIAL";
        }

        Map<String, Object> info = new HashMap<>();
        info.put("appId", appUser.getAppId());
        info.put("memberExpiresAt", memberExpiresAt);
        info.put("memberStatus", memberStatus);
        info.put("memberActive", "ACTIVE".equals(memberStatus));
        info.put("loginCount", appUser.getLoginCount());
        info.put("lastLoginAt", appUser.getLastLoginAt());

        // 试用信息
        info.put("trialActive", trialActive);
        info.put("trialExpiresAt", trial != null ? trial.getTrialExpiresAt() : null);
        info.put("trialApplied", trial != null);

        return info;
    }

    public List<PortalPricingPlan> getPricingPlans(Long appId) {
        return pricingPlanMapper.selectList(
            new LambdaQueryWrapper<PortalPricingPlan>()
                .eq(PortalPricingPlan::getAppId, appId)
                .eq(PortalPricingPlan::getEnabled, true)
                .orderByAsc(PortalPricingPlan::getSortOrder)
        );
    }
}
