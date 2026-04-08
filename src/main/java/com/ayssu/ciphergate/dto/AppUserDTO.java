package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用终端用户DTO
 */
@Data
@Schema(description = "应用终端用户信息")
public class AppUserDTO {
    
    @Schema(description = "用户ID")
    private Long id;
    
    @Schema(description = "所属应用ID", required = true)
    private Long appId;
    
    @Schema(description = "用户名", required = true, example = "user001")
    private String username;
    
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;
    
    @Schema(description = "手机号", example = "13800138000")
    private String phone;
    
    @Schema(description = "密码(创建时必填)", example = "password123")
    private String password;
    
    @Schema(description = "昵称", example = "张三")
    private String nickname;
    
    @Schema(description = "头像URL")
    private String avatarUrl;
    
    @Schema(description = "个性签名")
    private String signature;
}
