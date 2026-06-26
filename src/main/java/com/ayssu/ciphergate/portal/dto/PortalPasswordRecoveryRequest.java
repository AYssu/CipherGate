package com.ayssu.ciphergate.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PortalPasswordRecoveryRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    @NotNull(message = "应用ID不能为空")
    private Long appId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
