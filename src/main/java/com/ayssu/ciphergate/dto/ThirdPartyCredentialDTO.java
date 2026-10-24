package com.ayssu.ciphergate.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ThirdPartyCredentialDTO {
    /** 旧版单应用入参；新客户端请使用 appIds。 */
    private Long appId;
    /** 完整授权集合，非空，最多100个应用；编辑时不传则保持原绑定。 */
    private List<Long> appIds;
    private String name;
    private String allowedIps;
    private Integer dailyLimit;
    private Long totalCallLimit;
    private Long totalDaysLimit;
    private LocalDateTime expiresAt;
    private Integer status;
    private String remark;
}
