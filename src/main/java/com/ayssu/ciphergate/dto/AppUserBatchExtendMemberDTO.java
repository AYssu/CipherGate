package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "终端用户批量延长会员天数")
public class AppUserBatchExtendMemberDTO {

    @NotEmpty(message = "请至少选择一条终端用户")
    @Schema(description = "终端用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "延长天数不能为空")
    @Min(value = 1, message = "延长天数至少为1")
    @Schema(description = "延长天数", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer days;
}

