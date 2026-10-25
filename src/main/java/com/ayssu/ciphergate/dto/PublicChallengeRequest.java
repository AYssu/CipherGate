package com.ayssu.ciphergate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** One-use challenge scoped to a single action and normalized source IP. */
@Data
public class PublicChallengeRequest {
    @NotBlank @Size(max = 64)
    private String captchaId;
    @NotBlank @Size(max = 8)
    private String captchaCode;
}
