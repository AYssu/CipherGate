package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 卡密DTO
 */
@Data
@Schema(description = "卡密信息")
public class LicenseKeyDTO {
    
    @Schema(description = "卡密ID")
    private Long id;
    
    @Schema(description = "所属应用ID", required = true)
    private Long appId;
    
    @Schema(description = "自定义卡密码(留空自动生成)", example = "MYKEY123")
    private String keyCode;
    
    @Schema(description = "卡密类型", required = true, example = "MONTH")
    private String keyType;
    
    @Schema(description = "时长数值", example = "1")
    private Integer durationValue;
    
    @Schema(description = "时长单位", example = "MONTH")
    private String durationUnit;
    
    @Schema(description = "使用次数限制(0=不限)", example = "0")
    private Integer useLimit;
    
    @Schema(description = "解绑次数限制(0=不限)", example = "0")
    private Integer unbindLimit;
    
    @Schema(description = "可使用时间段-开始")
    private LocalTime useTimeStart;
    
    @Schema(description = "可使用时间段-结束")
    private LocalTime useTimeEnd;
    
    @Schema(description = "是否验证设备", example = "true")
    private Boolean deviceCheckEnabled;
    
    @Schema(description = "是否验证IP", example = "false")
    private Boolean ipCheckEnabled;
    
    @Schema(description = "备注")
    private String remark;
    
    @Schema(description = "核心标记数据")
    private String coreData;
    
    @Schema(description = "扩展元数据")
    private Map<String, Object> metadata;
}
