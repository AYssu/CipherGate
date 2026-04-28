package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryRequest;
import com.ayssu.ciphergate.dto.PublicAppUserExpireQueryResponse;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.service.PublicAppUserSelfService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PublicAppUserSelfServiceImpl implements PublicAppUserSelfService {

    private final AppUserMapper appUserMapper;

    @Override
    public PublicAppUserExpireQueryResponse queryExpire(PublicAppUserExpireQueryRequest req) {
        if (req.getAppId() == null || req.getAppId() <= 0) {
            throw new IllegalArgumentException("应用ID无效");
        }
        String email = req.getEmail() == null ? "" : req.getEmail().trim().toLowerCase();
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        AppUser user = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAppId, req.getAppId())
                .eq(AppUser::getEmail, email)
                .eq(AppUser::getDeleted, 0));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        LocalDateTime exp = user.getMemberExpiresAt();
        long remain = calcRemainingSeconds(exp);
        PublicAppUserExpireQueryResponse resp = new PublicAppUserExpireQueryResponse();
        resp.setEmailMasked(maskEmail(user.getEmail()));
        resp.setMemberExpiresAt(exp);
        resp.setRemainingSeconds(remain);
        resp.setMemberActive(exp != null && exp.isAfter(LocalDateTime.now()));
        return resp;
    }

    private static long calcRemainingSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return 0L;
        }
        long sec = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0L, sec);
    }

    private static String maskEmail(String emailRaw) {
        if (!StringUtils.hasText(emailRaw)) {
            return "";
        }
        String email = emailRaw.trim();
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int keep = Math.min(2, local.length());
        return local.substring(0, keep) + "***" + domain;
    }
}
