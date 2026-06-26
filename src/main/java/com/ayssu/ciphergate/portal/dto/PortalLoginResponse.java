package com.ayssu.ciphergate.portal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PortalLoginResponse {
    private String token;
    private List<PortalAppInfo> apps;
    private boolean needSelectApp;
}
