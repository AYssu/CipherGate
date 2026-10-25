package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Both known and unknown identities take the same queued request path; SMTP never leaks identity timing. */
@Slf4j
@Service
public class PortalRecoveryDispatcher {
    private final AppUserMapper users;
    private final PortalOtpService otp;
    private final ExecutorService executor;
    public PortalRecoveryDispatcher(AppUserMapper users, PortalOtpService otp,
            @Qualifier("portalRecoveryExecutor") ExecutorService executor) {
        this.users = users; this.otp = otp; this.executor = executor;
    }
    public void dispatch(String email) {
        try {
            executor.execute(() -> {
                try {
                    if (users.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEmail, email).eq(AppUser::getDeleted, 0)) > 0)
                        otp.send(email, "RECOVERY");
                } catch (Exception failure) {
                    // Mail providers may include recipients or credentials in their exception text.
                    log.warn("Recovery email delivery unavailable");
                }
            });
        } catch (RejectedExecutionException full) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务繁忙，请稍后重试");
        }
    }
}
