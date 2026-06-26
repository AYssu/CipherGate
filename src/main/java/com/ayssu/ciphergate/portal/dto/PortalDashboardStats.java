package com.ayssu.ciphergate.portal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PortalDashboardStats {
    private Integer totalLoginCount;
    private Integer portalLoginCount;
    private Integer appLoginCount;
    private Long todayOnlineSeconds;
    private Integer boundDeviceCount;
    private String lastLoginIp;
    private String lastLoginIpRegion;
    private String lastLoginAt;
    private List<Map<String, Object>> loginTrend;
    private List<Map<String, Object>> onlineTrend;
    private List<Map<String, Object>> ipDistribution;
}
