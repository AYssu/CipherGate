package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "终端用户批量封禁/解禁")
public class AppUserBatchBanDTO {

    @NotEmpty(message = "请至少选择一条终端用户")
    @Schema(description = "终端用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "ban 不能为空")
    @Schema(description = "true=封禁，false=解禁", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean ban;

    @Schema(description = "原因（封禁时可选）")
    private String reason;
}

