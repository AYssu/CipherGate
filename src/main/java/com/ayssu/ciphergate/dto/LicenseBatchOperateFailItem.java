package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "卡密批量操作失败项")
public class LicenseBatchOperateFailItem {

    @Schema(description = "卡密ID")
    private Long id;

    @Schema(description = "卡密码")
    private String keyCode;

    @Schema(description = "失败原因")
    private String reason;
}
