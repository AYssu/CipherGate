package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.portal.entity.PortalVerifyCode;
import com.ayssu.ciphergate.portal.mapper.PortalVerifyCodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        appUserMapper.updateById(user);
    }

    public void changePassword(Long appUserId, String oldPassword, String newPassword) {
        AppUser user = appUserMapper.selectById(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (user.getPassword() == null || !ENCODER.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        user.setPassword(ENCODER.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(user);
    }

    public void changeEmail(Long appUserId, String currentPassword, String newEmail, String verifyCode) {
        AppUser user = appUserMapper.selectById(appUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 1. 验证当前密码
        if (user.getPassword() == null || !ENCODER.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("当前密码错误");
        }

        // 2. 校验新邮箱验证码
        PortalVerifyCode code = verifyCodeMapper.selectOne(
            new LambdaQueryWrapper<PortalVerifyCode>()
                .eq(PortalVerifyCode::getEmail, newEmail)
                .eq(PortalVerifyCode::getPurpose, "EMAIL_CHANGE")
                .eq(PortalVerifyCode::getUsed, false)
                .orderByDesc(PortalVerifyCode::getId)
                .last("LIMIT 1")
        );

        if (code == null || code.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        if (!code.getCode().equals(verifyCode)) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 标记已使用
        code.setUsed(true);
        verifyCodeMapper.updateById(code);

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

        // 4. 更新邮箱
        user.setEmail(newEmail);
        user.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(user);
    }
}
