package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "卡密批量删除请求")
public class LicenseBatchDeleteDTO {

    @NotEmpty(message = "请至少选择一条卡密")
    @Schema(description = "卡密ID列表", required = true)
    private List<Long> ids;
}

