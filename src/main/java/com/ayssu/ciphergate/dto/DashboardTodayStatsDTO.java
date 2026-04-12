package com.ayssu.ciphergate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘「今日」聚合指标（自然日按服务器时区）。
 */
@Data
@Schema(description = "仪表盘今日统计")
public class DashboardTodayStatsDTO {

    @Schema(description = "今日首次激活的卡密数量（first_used_at 落在今日）")
    private long cardFirstActivatedToday;

    @Schema(description = "今日卡密登录成功次数（HTTP /card/login）")
    private long cardLoginToday;

    @Schema(description = "今日新注册的终端用户（app_user）")
    private long appUserRegisteredToday;

    @Schema(description = "今日终端用户 WS 登录成功次数（AUTH 成功）")
    private long appUserWsLoginToday;

    /** 仅 ADMIN / SUPER_ADMIN 返回；普通用户不序列化该字段 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "今日管理后台 GitHub 登录次数（仅管理员可见）")
    private Long platformLoginToday;
}
