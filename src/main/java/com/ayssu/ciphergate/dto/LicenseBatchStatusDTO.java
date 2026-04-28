package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卡密批量状态更新")
public class LicenseBatchStatusDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "状态不能为空")
    @Schema(description = "目标状态，当前用于批量封禁", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}
