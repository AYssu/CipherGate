package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卡密批量解绑")
public class LicenseBatchUnbindDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "是否解绑设备", example = "true")
    private Boolean unbindDevice;

    @Schema(description = "是否解绑IP", example = "false")
    private Boolean unbindIp;

    @AssertTrue(message = "请至少选择一种解绑类型")
    public boolean isAnyTargetSelected() {
        return Boolean.TRUE.equals(unbindDevice) || Boolean.TRUE.equals(unbindIp);
    }
}
