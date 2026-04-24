package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 开放试用接口请求参数
 */
@Data
public class OpenTrialRequest {

    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    @Schema(description = "终端用户邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.com")
    private String email;

    @NotBlank(message = "pid不能为空")
    @Schema(description = "应用ID（字符串）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private String pid;
}
