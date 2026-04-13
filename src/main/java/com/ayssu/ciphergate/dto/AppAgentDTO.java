package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "应用代理配置")
public class AppAgentDTO {
    private Long id;
    private Long appId;
    private String agentCode;
    private Long userId;
    /** ALL_IN_APP / OWN_ONLY */
    private String scopeMode;
    private Boolean enabled;
    private String remark;
    private List<String> permissions;
    private Map<String, Long> quotas;
}

