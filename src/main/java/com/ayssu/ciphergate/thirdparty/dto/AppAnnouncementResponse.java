package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 仅应用公告（出站前由统一 Advice 加密为 data HEX）。
 */
@Data
public class AppAnnouncementResponse {

    /**
     * 管理端配置的「应用公告」全文。
     */
    private String notice;

    /**
     * 服务端登记的应用当前版本（便于客户端展示或自行比对）。
     */
    private String currentVersion;

    /**
     * 服务端登记的最低支持版本。
     */
    private String minVersion;
}
