package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "批量加时结果")
public class LicenseBatchAddTimeResultDTO {

    @Schema(description = "成功数量")
    private int successCount;

    @Schema(description = "失败数量")
    private int failCount;

    @Schema(description = "失败明细")
    private List<LicenseBatchAddTimeFailItem> failures = new ArrayList<>();
}
