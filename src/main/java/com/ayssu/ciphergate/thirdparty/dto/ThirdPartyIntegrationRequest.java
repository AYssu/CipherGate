package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/** Validated inside the integration service so rejected attempts also produce audit records. */
@Data
public class ThirdPartyIntegrationRequest {
    private String apiKey;
    private Long appId;
    private String userEmail;
    private Long timestamp;
    private String nonce;
    private String sign;
}
