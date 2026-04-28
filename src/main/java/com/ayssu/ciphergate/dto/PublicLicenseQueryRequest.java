package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "公开-卡密查询剩余时间请求")
public class PublicLicenseQueryRequest {

    @NotNull(message = "应用ID不能为空")
    @Schema(description = "应用ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long appId;

    @NotBlank(message = "卡密不能为空")
    @Schema(description = "卡密", example = "ABCDEFGHJKLMNPQR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyCode;
}
