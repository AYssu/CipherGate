package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "仪表盘总览统计")
public class DashboardOverviewDTO {

    @Schema(description = "应用总数（当前用户 owner 维度）")
    private long appCount;

    @Schema(description = "终端用户总数（app_user）")
    private long appUserTotal;

    @Schema(description = "卡密总数（license_key）")
    private long licenseTotal;

    @Schema(description = "近7天卡密登录总次数（access_event）")
    private long cardLogin7d;

    @Schema(description = "近7天终端用户登录总次数（WS AUTH 成功）")
    private long appUserWsLogin7d;
}
