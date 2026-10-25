package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.portal.entity.PortalVerifyCode;
import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortalSettingsService {

    private final AppUserMapper appUserMapper;
    private final PortalVerifyCodeMapper verifyCodeMapper;
    private final PortalOtpService otpService;
    private final com.ayssu.ciphergate.service.PublicAbuseGuard abuseGuard;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public Map<String, Object> getProfile(Long appUserId) {
        AppUser user = appUserMapper.selectById(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("username", user.getUsername());
        profile.put("nickname", user.getNickname());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }

    public void updateNickname(Long appUserId, String nickname) {
        AppUser user = appUserMapper.selectById(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setNickname(nickname);
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getId, user.getId()).set(user.getNickname() != null, AppUser::getNickname, user.getNickname())
            .set(AppUser::getUpdatedAt, user.getUpdatedAt()));
    }

    @org.springframework.transaction.annotation.Transactional
    public void changePassword(Long appUserId, String oldPassword, String newPassword) {
        com.ayssu.ciphergate.util.PasswordPolicy.validate(newPassword);
        abuseGuard.check("portal-password-change", appUserId.toString(), 10, java.time.Duration.ofMinutes(15));
        AppUser user = appUserMapper.selectForUpdate(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (user.getPassword() == null || !ENCODER.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        user.setPassword(ENCODER.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        int changed = appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getId, user.getId()).set(AppUser::getPassword, user.getPassword())
            .set(AppUser::getUpdatedAt, user.getUpdatedAt()));
        if (changed != 1) throw new IllegalStateException("密码更新失败，请重试");
    }

    @org.springframework.transaction.annotation.Transactional
    public void changeEmail(Long appUserId, String currentPassword, String newEmail, String verifyCode) {
        newEmail = PortalAuthService.normalizeEmail(newEmail);
        abuseGuard.check("portal-email-change", appUserId.toString(), 10, java.time.Duration.ofMinutes(15));
        AppUser user = appUserMapper.selectForUpdate(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 1. 验证当前密码
        if (user.getPassword() == null || !ENCODER.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("当前密码错误");
        }

        // 3. 检查新邮箱是否已被其他用户在同一应用中使用
        Long count = appUserMapper.selectCount(
            new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, user.getAppId())
                .eq(AppUser::getEmail, newEmail)
                .ne(AppUser::getId, appUserId)
                .eq(AppUser::getDeleted, 0)
        );

        if (count > 0) {
            throw new IllegalArgumentException("该邮箱已被其他用户使用");
        }

        otpService.verify(newEmail, PortalOtpService.emailChangePurpose(user), verifyCode, true);

        // 4. 更新邮箱
        user.setEmail(newEmail);
        user.setUpdatedAt(LocalDateTime.now());
        int changed = appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getId, user.getId()).set(user.getEmail() != null, AppUser::getEmail, user.getEmail())
            .set(AppUser::getUpdatedAt, user.getUpdatedAt()));
        if (changed != 1) throw new IllegalStateException("邮箱更新失败，请重试");
    }
}
