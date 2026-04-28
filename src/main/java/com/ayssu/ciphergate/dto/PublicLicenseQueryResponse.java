package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "公开-卡密查询剩余时间响应")
public class PublicLicenseQueryResponse {

    @Schema(description = "卡密（可能脱敏）", example = "ABCD****PQ")
    private String keyCodeMasked;

    @Schema(description = "状态: 1=未使用, 2=使用中, 3=已到期, 4=已禁用", example = "2")
    private Integer status;

    @Schema(description = "到期时间；永久卡为空")
    private LocalDateTime expiresAt;

    @Schema(description = "剩余秒数；永久卡为 -1", example = "3600")
    private Long remainingSeconds;

    @Schema(description = "是否绑定设备", example = "true")
    private Boolean boundDevice;

    @Schema(description = "是否绑定IP", example = "false")
    private Boolean boundIp;

    @Schema(description = "已使用解绑次数", example = "1")
    private Integer unbindCount;

    @Schema(description = "解绑次数上限；0 表示不限制", example = "3")
    private Integer unbindLimit;

    @Schema(description = "剩余可解绑次数；-1 表示不限制", example = "2")
    private Integer unbindRemaining;
}
