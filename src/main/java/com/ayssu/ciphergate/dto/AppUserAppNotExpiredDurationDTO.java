package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "按应用筛选「未到期会员」并批量加/扣时")
public class AppUserAppNotExpiredDurationDTO {

    @NotNull(message = "appId 不能为空")
    @Schema(description = "应用ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long appId;

    @Schema(description = "数值（永久不支持；单位非 PERMANENT 时必填）", example = "30")
    private Integer amount;

    @NotBlank(message = "单位不能为空")
    @Schema(description = "单位：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR", example = "DAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unit;
}

