package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 三方卡密换绑（解密后的业务体，字段名需与客户端 canonical 一致）。
 */
@Data
public class CardRebindRequest {

    /** 卡密 */
    private String cardCode;

    /** 新的设备标识 */
    private String deviceId;
}
