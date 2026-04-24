package com.ayssu.ciphergate.dto;

import lombok.Data;

@Data
public class ThirdPartyCredentialQueryDTO {
    private Long appId;
    private String name;
    private String apiKey;
    private Integer status;
    private Integer current = 1;
    private Integer size = 10;
}
