package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "应用用户自助注册：提交注册")
public class AppUserRegisterSubmitRequest {

    @NotNull
    @Schema(description = "应用 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long appId;

    @NotBlank
    @Size(min = 2, max = 50, message = "用户名须为 2～50 个字符")
    @Schema(description = "登录用户名（应用内唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank
    @Email
    @Schema(description = "注册邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码须为 6 位数字")
    @Schema(description = "邮箱验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String emailCode;

    @NotBlank
    @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码须同时包含字母与数字，至少 8 位")
    @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
