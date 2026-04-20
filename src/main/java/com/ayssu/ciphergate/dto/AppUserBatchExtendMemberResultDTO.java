package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "终端用户批量延长会员结果")
public class AppUserBatchExtendMemberResultDTO {

    @Schema(description = "成功数量")
    private int successCount;

    @Schema(description = "失败数量")
    private int failCount;

    @Schema(description = "失败明细")
    private List<AppUserBatchExtendMemberFailItem> failures = new ArrayList<>();
}

