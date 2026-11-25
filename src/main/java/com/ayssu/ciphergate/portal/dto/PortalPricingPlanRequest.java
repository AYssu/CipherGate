package com.ayssu.ciphergate.portal.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PortalPricingPlanRequest {
    @NotBlank @Size(max = 100)
    private String planName;
    @NotBlank @Pattern(regexp = "MEMBER|TRIAL|DAY|DAYS|PERMANENT")
    private String planType;
    @Min(1)
    private Integer durationDays;
    @NotNull @Min(1)
    private Long priceFen;
    @Min(1)
    private Long originalPriceFen;
    @Min(0)
    private Integer stockRemaining;
    @Min(0)
    private Long revision;
    @Min(0)
    private Integer sortOrder;
    private Boolean enabled;
}
