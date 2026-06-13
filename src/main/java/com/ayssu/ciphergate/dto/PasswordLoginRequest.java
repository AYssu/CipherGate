package com.ayssu.ciphergate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码登录请求
 */
@Data
public class PasswordLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String login;

    @NotBlank(message = "密码不能为空")
    private String password;
}
