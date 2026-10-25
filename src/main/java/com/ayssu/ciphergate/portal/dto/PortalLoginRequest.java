package com.ayssu.ciphergate.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortalLoginRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @jakarta.validation.constraints.Size(max = 254)
    private String email;

    @NotBlank(message = "密码不能为空")
    @jakarta.validation.constraints.Size(max = 100)
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;
}
