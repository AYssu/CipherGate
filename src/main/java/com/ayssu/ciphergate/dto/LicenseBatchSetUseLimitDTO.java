package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卡密批量设置使用次数限制")
public class LicenseBatchSetUseLimitDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "使用次数限制不能为空")
    @Min(value = 0, message = "使用次数限制不能小于0")
    @Schema(description = "使用次数限制，0 表示不限制", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer useLimit;
}
