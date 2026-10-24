package com.ayssu.ciphergate.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AppUserLicenseRechargeRequest {
    @NotNull @Positive
    private Long appId;
    @NotBlank @Email @Size(max = 100)
    private String email;
    @NotBlank @Size(max = 100)
    private String keyCode;
}
