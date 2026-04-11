package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量加时失败项")
public class LicenseBatchAddTimeFailItem {

    @Schema(description = "卡密ID")
    private Long id;

    @Schema(description = "卡密码（可能为空）")
    private String keyCode;

    @Schema(description = "失败原因")
    private String reason;
}
