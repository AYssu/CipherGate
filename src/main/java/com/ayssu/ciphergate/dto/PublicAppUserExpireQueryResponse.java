package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "公开-终端用户到期查询响应")
public class PublicAppUserExpireQueryResponse {

    @Schema(description = "邮箱（脱敏）", example = "ab***@mail.com")
    private String emailMasked;

    @Schema(description = "会员到期时间；为空表示未开通会员")
    private LocalDateTime memberExpiresAt;

    @Schema(description = "剩余秒数；未开通会员为 0", example = "3600")
    private Long remainingSeconds;

    @Schema(description = "是否会员有效", example = "true")
    private Boolean memberActive;
}
