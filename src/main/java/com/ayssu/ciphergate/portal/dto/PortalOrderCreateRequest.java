package com.ayssu.ciphergate.portal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PortalOrderCreateRequest {

    @NotNull(message = "方案ID不能为空")
    private Long planId;
}
