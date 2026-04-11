package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "直接设置会员到期时间；memberExpiresAt 为 null 表示清空（取消会员）")
public class MemberExpiresAtDTO {

    @Schema(description = "会员到期时间，null 表示清空")
    private LocalDateTime memberExpiresAt;
}
