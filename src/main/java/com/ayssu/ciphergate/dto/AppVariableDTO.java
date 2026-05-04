package com.ayssu.ciphergate.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

/**
 * 应用变量DTO
 */
@Data
public class AppVariableDTO {
    
    private Long id;
    
    @NotNull(message = "应用ID不能为空")
    private Long appId;
    
    @NotBlank(message = "变量名称不能为空")
    @Size(min = 1, max = 100, message = "变量名称长度为1-100位")
    private String variableName;
    
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 200, message = "显示名称长度不能超过200位")
    private String displayName;
    
    @Size(max = 500, message = "描述长度不能超过500位")
    private String description;
    
    @NotBlank(message = "变量类型不能为空")
    @Pattern(regexp = "^(STRING|NUMBER|BOOLEAN|JSON|ARRAY)$", message = "变量类型必须是: STRING, NUMBER, BOOLEAN, JSON, ARRAY")
    private String variableType;
    
    private String variableValue;
    
    private Boolean required = false;
    
    private Integer sortOrder = 0;
    
    private String validationRules;
    
    private String options;
    
    private Boolean enabled = true;
    
    @Size(max = 50, message = "版本号长度不能超过50位")
    private String version;
    
    private String tags;
    
    private Map<String, Object> metadata;

    /**
     * 安全分级：0=STANDARD，1=SENSITIVE，2=CRITICAL（默认 2）
     */
    @Min(0)
    @Max(2)
    private Integer securityTier = 2;
    
    @Size(max = 500, message = "变更原因长度不能超过500位")
    private String changeReason;
}