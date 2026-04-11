package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卡密批量加时")
public class LicenseBatchAddTimeDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "加时数值不能为空")
    @Min(value = 1, message = "加时数值至少为1")
    @Schema(description = "时长数值", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer durationValue;

    @NotBlank(message = "加时单位不能为空")
    @Schema(description = "时长单位：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR", example = "DAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String durationUnit;
}
