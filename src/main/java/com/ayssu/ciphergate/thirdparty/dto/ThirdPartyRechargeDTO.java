package com.ayssu.ciphergate.thirdparty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ThirdPartyRechargeDTO {
    @NotBlank(message = "apiKey不能为空")
    @Schema(description = "三方服务 apiKey")
    private String apiKey;

    @Email(message = "userEmail格式错误")
    @NotBlank(message = "userEmail不能为空")
    @Schema(description = "充值目标终端用户邮箱")
    private String userEmail;

    @NotBlank(message = "projectKey不能为空")
    @Schema(description = "易验证项目标识")
    private String projectKey;

    @NotNull(message = "days不能为空")
    @Min(value = 1, message = "days至少为1")
    @Schema(description = "充值天数", minimum = "1")
    private Integer days;

    @NotNull(message = "timestamp不能为空")
    @Schema(description = "毫秒时间戳")
    private Long timestamp;

    @NotBlank(message = "sign不能为空")
    @Schema(description = "按服务端约定生成的签名")
    private String sign;

    @Schema(description = "可选的业务订单号，用于幂等和排查")
    private String outTradeNo;
}
