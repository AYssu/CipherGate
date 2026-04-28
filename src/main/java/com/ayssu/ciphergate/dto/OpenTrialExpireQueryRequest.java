package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 开放试用到期时间查询参数
 */
@Data
public class OpenTrialExpireQueryRequest {

    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    @Schema(description = "终端用户邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.com")
    private String email;

    @NotNull(message = "appId不能为空")
    @Positive(message = "appId必须大于0")
    @Schema(description = "应用ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;
}
