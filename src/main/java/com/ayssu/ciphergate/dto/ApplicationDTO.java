package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 应用DTO
 */
@Data
@Schema(description = "应用信息")
public class ApplicationDTO {
    
    @Schema(description = "应用ID")
    private Long id;
    
    @Schema(description = "应用名称", required = true, example = "我的应用")
    private String appName;
    
    @Schema(description = "应用描述", example = "这是一个测试应用")
    private String description;
    
    @Schema(description = "应用公告")
    private String notice;
    
    @Schema(description = "更新公告")
    private String updateNotice;
    
    @Schema(description = "更新文件存储Key(MinIO)")
    private String updateFileStorageKey;
    
    @Schema(description = "应用分类", example = "游戏")
    private String category;
    
    @Schema(description = "标签(逗号分隔)", example = "游戏,娱乐")
    private String tags;
    
    @Schema(description = "应用图标URL")
    private String iconUrl;
    
    @Schema(description = "业务模式: 1=付费, 2=免费, 3=试用+付费", required = true, example = "1")
    private Integer businessModel;
    
    @Schema(description = "状态: 1=正常, 2=维护, 3=停用", example = "1")
    private Integer status;
    
    @Schema(description = "加密插件标识", example = "aes-default")
    private String encryptionPlugin;
    
    @Schema(description = "加密配置参数")
    private Map<String, Object> encryptionConfig;
    
    @Schema(description = "功能开关配置")
    private Map<String, Object> features;
    
    @Schema(description = "流量限制(字节)", example = "0")
    private Long trafficLimit;
    
    @Schema(description = "当前版本号", example = "1.0.0")
    private String currentVersion;
    
    @Schema(description = "最低支持版本", example = "1.0.0")
    private String minVersion;

    @Schema(description = "解绑扣时模式: NONE=不扣, PERCENT=按剩余时长百分比, HOURS=固定扣小时", example = "NONE")
    private String unbindTimeDeductMode;

    @Schema(description = "扣时数值：PERCENT 为 0-100；HOURS 为小时数；NONE 时可不传", example = "0")
    private BigDecimal unbindTimeDeductValue;
}
