package com.ayssu.ciphergate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置/修改密码请求（已登录用户）
 */
@Data
public class SetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100 个字符")
    private String newPassword;
}
