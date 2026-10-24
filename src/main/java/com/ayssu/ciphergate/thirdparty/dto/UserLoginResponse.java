package com.ayssu.ciphergate.thirdparty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "一次性用户授权结果，不创建 WS 会话；不是门户 JWT 或卡密心跳凭证")
public class UserLoginResponse {
    private Long appId;
    private Long userId;
    private String username;
    private String nickname;
    @Schema(description = "是否要求会员有效期；免费应用为 false，但仍校验账号密码和设备")
    private Boolean memberRequired;
    @Schema(description = "用户实际会员到期时间；免费应用可为空，与卡密响应一样加密前转 epoch 毫秒")
    private LocalDateTime expiresAt;
    @Schema(description = "会员剩余秒数；免费应用为 null，不能按 0 或缺失判定免费用户未授权")
    private Long available;
    private Integer loginCount;
    private LocalDateTime lastLoginAt;
    private Long bindingCount;
    @Schema(description = "JSON 字符串，与卡密 variables 的格式一致")
    private String variables;
    @Schema(description = "成功登录为 true：最近5分钟 HTTP 活跃的展示口径，不表示 WS 连接，不累计在线时长")
    private Boolean online;
    @Schema(description = "固定 null：本接口不签发心跳或门户 token")
    private String token;
    @Schema(description = "固定 USER，仅用于统计展示")
    private String identityType;
    @Schema(description = "user_ 加用户 ID，仅用于统计展示，不是授权凭证")
    private String identityId;
}
