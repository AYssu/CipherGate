package com.ayssu.ciphergate.thirdparty.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ThirdPartyRechargeDTO {
    @NotBlank(message = "apiKey不能为空")
    private String apiKey;

    @Email(message = "userEmail格式错误")
    @NotBlank(message = "userEmail不能为空")
    private String userEmail;

    @NotBlank(message = "projectKey不能为空")
    private String projectKey;

    @NotNull(message = "days不能为空")
    @Min(value = 1, message = "days至少为1")
    private Integer days;

    @NotNull(message = "timestamp不能为空")
    private Long timestamp;

    @NotBlank(message = "sign不能为空")
    private String sign;

    private String outTradeNo;
}
