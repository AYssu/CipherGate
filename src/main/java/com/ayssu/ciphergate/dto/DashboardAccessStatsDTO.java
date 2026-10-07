package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成功登录活跃统计，无需心跳。自然日按服务器时区，设备按应用隔离去重；不代表实时在线或真实人数。")
public class DashboardAccessStatsDTO {
    private long paidCardLoginToday;
    private long freeLoginToday;
    private long activeCardToday;
    private long activeVisitorToday;
    private long activeDeviceToday;
    private long activeCard7d;
    private long activeVisitor7d;
    private long activeDevice7d;
    @Schema(description = "最近5分钟成功登录的不同卡密数，不是实时在线数")
    private long recentCardCount;
    @Schema(description = "最近5分钟成功登录的不同免费访客设备数，不是实时在线数")
    private long recentVisitorCount;
    @Schema(description = "近7天缺少设备标识的历史免费登录次数，仍计入次数但不参与访客去重")
    private long unidentifiedFreeLogin7d;
}
