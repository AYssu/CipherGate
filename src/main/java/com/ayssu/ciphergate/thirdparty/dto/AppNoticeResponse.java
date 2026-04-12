package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 检查更新接口的响应：公告与安装包信息（出站前由统一 Advice 加密为 data HEX）。
 * <p>
 * 当 {@link #isLatestVersion} 为 true 时仅填充 {@link #notice}；为 false 时填充 {@link #updateNotice} 与可选的 {@link #updateDownloadUrl}。
 */
@Data
public class AppNoticeResponse {

    /**
     * 是否为服务端认定的「已跟当前主线版本一致」（双方均为合法 x.x.x 且相等）。
     */
    private Boolean isLatestVersion;

    /**
     * 请求中的客户端版本号（回显，便于客户端核对）。
     */
    private String clientVersion;

    /**
     * 服务端登记的应用当前版本号（作为支持区间上界，与 {@link #minVersion} 组成 [min, current]）。
     */
    private String currentVersion;

    /**
     * 服务端登记的最低支持版本（下界）。
     */
    private String minVersion;

    /**
     * 本次是否按 x.x.x 做了区间校验（非 x.x.x 的客户端版本字符串不做区间比较）。
     */
    private Boolean versionRangeChecked;

    /**
     * 最新主线时的软件公告（{@code isLatestVersion == true} 时有值）。
     */
    private String notice;

    /**
     * 客户端落后主线时的更新说明（{@code isLatestVersion == false} 时有值）。
     */
    private String updateNotice;

    /**
     * 本服务更新包下载入口（{@code GET /api/v1/app/update-package?ticket=}），便于经网关代理；票据默认 5 分钟有效。
     */
    private String updateDownloadUrl;
}
