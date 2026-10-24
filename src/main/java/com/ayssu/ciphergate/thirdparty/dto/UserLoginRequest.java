package com.ayssu.ciphergate.thirdparty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
@Schema(description = "三方 HTTP 用户登录的解密后参数；实际请求仍需应用签名和加密")
public class UserLoginRequest {
    @Schema(description = "当前应用下的用户名或邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @ToString.Exclude
    @Schema(description = "用户密码，不做 trim", requiredMode = Schema.RequiredMode.REQUIRED,
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
    @Schema(description = "设备标识，最多255字符", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;
    @Schema(description = "设备名称，可选，最多100字符")
    private String deviceName;
    @Schema(description = "设备系统，可选，最多50字符")
    private String deviceOs;
}
