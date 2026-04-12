package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 三方仅获取应用公告（解密后的业务体，字段名需与客户端 canonical 一致）。
 * <p>
 * 与 {@link AppNoticeRequest} 类似：AES 解密后的 canonical 须<strong>非空</strong>，故需至少一个键值；
 * 客户端可固定传 {@code ping=1}（或其它无业务含义短串）。
 */
@Data
public class AppAnnouncementRequest {

    /**
     * 占位字段，满足非空解密；不参与业务逻辑。
     */
    private String ping;
}
