package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "终端用户批量操作：仅 ID 列表")
public class AppUserBatchIdsDTO {

    @NotEmpty(message = "请至少选择一条终端用户")
    @Schema(description = "终端用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;
}

