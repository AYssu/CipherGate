package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理员延长会员天数（在「当前时间」与「原到期时间」中较晚者基础上累加）")
public class ExtendMemberDaysDTO {

    @NotNull
    @Min(1)
    @Max(36500)
    @Schema(description = "增加的天数", example = "30")
    private Integer days;
}
