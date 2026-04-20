package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用终端用户查询DTO
 */
@Data
@Schema(description = "应用终端用户查询条件")
public class AppUserQueryDTO {
    
    @Schema(description = "应用ID")
    private Long appId;
    
    @Schema(description = "用户名(模糊查询)")
    private String username;
    
    @Schema(description = "邮箱(模糊查询)")
    private String email;
    
    @Schema(description = "手机号(模糊查询)")
    private String phone;
    
    @Schema(description = "昵称(模糊查询)")
    private String nickname;

    @Schema(description = "封禁状态：true=已封禁，false=正常")
    private Boolean banned;

    /**
     * 会员状态：
     * - ACTIVE：未到期（memberExpiresAt > now）
     * - EXPIRED：已到期（memberExpiresAt <= now）
     * - NONE：未开通（memberExpiresAt is null）
     */
    @Schema(description = "会员状态：ACTIVE/EXPIRED/NONE")
    private String memberStatus;

    @Schema(description = "WS 在线状态：true=在线，false=离线（单机内存）")
    private Boolean wsOnline;
    
    @Schema(description = "当前页", example = "1")
    private Integer current = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
