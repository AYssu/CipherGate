package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "仪表盘趋势点")
public class DashboardTrendPointDTO {
    @Schema(description = "日期（yyyy-MM-dd）")
    private String date;

    @Schema(description = "当日新增终端用户数")
    private long appUserRegistered;

    @Schema(description = "当日卡密登录次数")
    private long cardLogin;

    @Schema(description = "当日终端用户WS登录次数")
    private long appUserWsLogin;
}
