package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@Schema(description = "卡密批量设置使用时间段")
public class LicenseBatchSetUseTimeDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "开始时间，格式 HH:mm:ss", example = "09:00:00")
    private LocalTime useTimeStart;

    @Schema(description = "结束时间，格式 HH:mm:ss", example = "22:00:00")
    private LocalTime useTimeEnd;

    @Schema(description = "是否清空时间段限制", example = "false")
    private Boolean clearTimeRange;

    @AssertTrue(message = "请设置时间段，或勾选清空时间段限制")
    public boolean isTimeRangeValid() {
        if (Boolean.TRUE.equals(clearTimeRange)) {
            return true;
        }
        return useTimeStart != null && useTimeEnd != null;
    }
}
