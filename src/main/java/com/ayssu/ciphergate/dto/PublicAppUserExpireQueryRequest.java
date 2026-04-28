package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "公开-终端用户到期查询请求")
public class PublicAppUserExpireQueryRequest {

    @NotNull(message = "应用ID不能为空")
    @Schema(description = "应用ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long appId;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "终端用户邮箱", example = "name@company.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
}
