package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.OpenTrialRequest;

import java.time.LocalDateTime;

public interface OpenTrialService {

    TrialApplyResult applyTrial(OpenTrialRequest request, String ipAddress, String userAgent);

    TrialExpireResult queryTrialExpireAt(Long appId, String email);

    record TrialApplyResult(Long appId, Long userId, String email, Integer trialDays, LocalDateTime expiresAt) {}

    record TrialExpireResult(Long appId, Long userId, String email, LocalDateTime expiresAt) {}
}
