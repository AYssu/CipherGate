package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量生成卡密DTO
 */
@Data
@Schema(description = "批量生成卡密")
public class LicenseBatchCreateDTO {
    
    @Schema(description = "所属应用ID", required = true)
    private Long appId;
    
    @Schema(description = "批次名称", required = true, example = "2024年1月批次")
    private String batchName;
    
    @Schema(description = "卡密类型", required = true, example = "MONTH")
    private String keyType;
    
    @Schema(description = "时长数值", example = "1")
    private Integer durationValue;
    
    @Schema(description = "时长单位", example = "DAY")
    private String durationUnit;
    
    @Schema(description = "生成数量", required = true, example = "100")
    private Integer totalCount;
    
    @Schema(description = "使用次数限制(0=不限)", example = "0")
    private Integer useLimit;
    
    @Schema(description = "解绑次数限制(0=不限)", example = "0")
    private Integer unbindLimit;
    
    @Schema(description = "是否验证设备", example = "true")
    private Boolean deviceCheckEnabled;
    
    @Schema(description = "是否验证IP", example = "false")
    private Boolean ipCheckEnabled;
    
    @Schema(description = "备注")
    private String remark;
}
