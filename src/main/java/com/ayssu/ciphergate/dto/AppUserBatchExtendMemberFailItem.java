package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "终端用户批量延长会员失败项")
public class AppUserBatchExtendMemberFailItem {

    @Schema(description = "终端用户ID")
    private Long id;

    @Schema(description = "用户名（可能为空）")
    private String username;

    @Schema(description = "失败原因")
    private String reason;
}

