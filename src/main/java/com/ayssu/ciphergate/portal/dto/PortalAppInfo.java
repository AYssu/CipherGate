package com.ayssu.ciphergate.portal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortalAppInfo {
    private Long appId;
    private String appName;
    private String iconUrl;
    private Boolean memberActive;
    private String memberExpiresAt;
}
