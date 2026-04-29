package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "仪表盘在线统计")
public class DashboardOnlineDTO {

    @Schema(description = "当前在线卡密数量（last_used_at 最近5分钟）")
    private long cardOnlineCount;

    @Schema(description = "当前在线终端用户数量（WS 会话在线）")
    private long appUserOnlineCount;
}
