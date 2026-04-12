package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "应用用户自助注册：发送邮箱验证码")
public class AppUserRegisterSendCodeRequest {

    @NotNull
    @Schema(description = "应用 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long appId;

    @NotBlank
    @Email
    @Schema(description = "注册邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
}
