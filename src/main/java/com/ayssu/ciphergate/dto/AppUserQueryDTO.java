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
    
    @Schema(description = "当前页", example = "1")
    private Integer current = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
