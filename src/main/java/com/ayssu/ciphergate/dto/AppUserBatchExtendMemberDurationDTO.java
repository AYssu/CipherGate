package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "终端用户批量按单位延长会员")
public class AppUserBatchExtendMemberDurationDTO {

    @NotEmpty(message = "请至少选择一条终端用户")
    @Schema(description = "终端用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "数值（永久可不传）", example = "30")
    @Min(value = 1, message = "数值至少为1")
    @Max(value = 36500, message = "数值过大")
    private Integer amount;

    @NotBlank(message = "单位不能为空")
    @Schema(description = "单位：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR/PERMANENT", example = "DAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unit;
}

