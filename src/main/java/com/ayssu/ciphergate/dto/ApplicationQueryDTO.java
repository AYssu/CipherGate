package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用查询DTO
 */
@Data
@Schema(description = "应用查询条件")
public class ApplicationQueryDTO {
    
    @Schema(description = "应用名称（模糊查询）", example = "我的应用")
    private String appName;
    
    @Schema(description = "应用分类", example = "游戏")
    private String category;
    
    @Schema(description = "业务模式: 1=付费, 2=免费, 3=试用+付费", example = "1")
    private Integer businessModel;
    
    @Schema(description = "状态: 1=正常, 2=维护, 3=停用", example = "1")
    private Integer status;
    
    @Schema(description = "所属用户ID")
    private Long ownerId;
    
    @Schema(description = "当前页码", example = "1")
    private Integer current = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
