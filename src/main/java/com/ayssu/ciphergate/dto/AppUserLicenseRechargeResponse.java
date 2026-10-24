package com.ayssu.ciphergate.dto;

import java.time.LocalDateTime;

public record AppUserLicenseRechargeResponse(String emailMasked, String keyCodeMasked,
        LocalDateTime beforeExpiresAt, LocalDateTime memberExpiresAt,
        long remainingSeconds, boolean memberActive) { }
