package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * 卡密查询DTO
 */
@Data
@Schema(description = "卡密查询条件")
public class LicenseKeyQueryDTO {
    
    @Schema(description = "应用ID")
    private Long appId;
    
    @Schema(description = "卡密码(模糊查询)")
    private String keyCode;
    
    @Schema(description = "备注(模糊查询)")
    private String remark;

    @Schema(description = "卡密类型")
    private String keyType;
    
    @Schema(description = "批次ID")
    private Long batchId;

    @Schema(description = "批次名称(模糊查询)")
    private String batchName;
    
    @Schema(description = "状态")
    private Integer status;
    
    @Schema(description = "创建者ID")
    private Long ownerId;
    
    @Schema(description = "是否在线")
    private Boolean isOnline;

    @Schema(description = "导出/查询指定卡密ID列表")
    private List<Long> ids;
    
    @Schema(description = "当前页", example = "1")
    private Integer current = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
