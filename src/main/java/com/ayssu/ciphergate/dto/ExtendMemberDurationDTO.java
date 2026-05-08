package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理员按单位延长会员（在「当前时间」与「原到期时间」中较晚者基础上累加）")
public class ExtendMemberDurationDTO {

    @Schema(description = "数值（永久可不传）", example = "30")
    @Min(1)
    @Max(36500)
    private Integer amount;

    @NotBlank
    @Schema(description = "单位：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR/PERMANENT", example = "DAY")
    private String unit;
}

