package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.OpenTrialRequest;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserTrial;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.AppUserTrialMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.ActivityLogService;
import com.ayssu.ciphergate.service.OpenTrialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class OpenTrialServiceImpl implements OpenTrialService {

    private static final int TRIAL_DAYS = 1;
    private static final int BUSINESS_MODEL_TRIAL_AND_PAID = 3;

    private final ApplicationMapper applicationMapper;
    private final AppUserMapper appUserMapper;
    private final AppUserTrialMapper appUserTrialMapper;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrialApplyResult applyTrial(OpenTrialRequest request, String ipAddress, String userAgent) {
        Long appId = parsePid(request.getPid());
        String email = normalizeEmail(request.getEmail());

        Application app = applicationMapper.selectById(appId);
        if (app == null || app.getDeleted() != null && app.getDeleted() == 1) {
            throw new IllegalArgumentException("应用不存在");
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new IllegalStateException("应用未启用");
        }
        if (app.getBusinessModel() == null || app.getBusinessModel() != BUSINESS_MODEL_TRIAL_AND_PAID) {
            throw new IllegalStateException("当前应用未开启试用");
        }

        AppUser appUser = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0)
                .last("limit 1"));
        if (appUser == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = appUser.getMemberExpiresAt();
        if (base == null || !base.isAfter(now)) {
            base = now;
        }
        LocalDateTime newExpiresAt = base.plus(TRIAL_DAYS, ChronoUnit.DAYS);

        AppUserTrial trial = new AppUserTrial();
        trial.setAppId(appId);
        trial.setUserId(appUser.getId());
        trial.setTrialStartedAt(now);
        trial.setTrialExpiresAt(newExpiresAt);
        try {
            appUserTrialMapper.insert(trial);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("该用户已申请过试用");
        }

        appUser.setMemberExpiresAt(newExpiresAt);
        appUser.setUpdatedAt(now);
        appUserMapper.updateById(appUser);

        if (app.getOwnerId() != null) {
            activityLogService.log(
                    app.getOwnerId(),
                    "open_trial",
                    "OPEN_TRIAL_APPLY",
                    "APP_USER",
                    "开放试用申请成功 appId=" + appId + ", email=" + email + ", days=" + TRIAL_DAYS,
                    trim(ipAddress),
                    trim(userAgent),
                    "SUCCESS",
                    "LOW"
            );
        }

        return new TrialApplyResult(appId, appUser.getId(), email, TRIAL_DAYS, newExpiresAt);
    }

    @Override
    public TrialExpireResult queryTrialExpireAt(Long appId, String email) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("appId必须大于0");
        }
        String normalizedEmail = normalizeEmail(email);

        AppUser appUser = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, appId)
                .eq(AppUser::getEmail, normalizedEmail)
                .eq(AppUser::getDeleted, 0)
                .last("limit 1"));
        if (appUser == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        return new TrialExpireResult(appId, appUser.getId(), normalizedEmail, appUser.getMemberExpiresAt());
    }

    private static Long parsePid(String pid) {
        if (!StringUtils.hasText(pid)) {
            throw new IllegalArgumentException("pid不能为空");
        }
        try {
            long v = Long.parseLong(pid.trim());
            if (v <= 0) {
                throw new IllegalArgumentException("pid必须大于0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("pid格式不正确");
        }
    }

    private static String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email不能为空");
        }
        return email.trim().toLowerCase();
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
