package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 三方「检查更新」请求体（{@code POST /api/v1/app/update-check}，解密后的业务体，字段名需与客户端 canonical 一致）。
 */
@Data
public class AppNoticeRequest {

    /**
     * 客户端当前软件版本号（由业务自行约定格式，如 semver）。
     */
    private String version;
}
