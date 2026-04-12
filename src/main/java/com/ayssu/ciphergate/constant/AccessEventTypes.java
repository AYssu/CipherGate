package com.ayssu.ciphergate.constant;

/**
 * {@code access_event.event_type} 取值。
 */
public final class AccessEventTypes {

    private AccessEventTypes() {
    }

    /** 三方卡密 HTTP 登录（每次成功 +1） */
    public static final String CARD_LOGIN = "CARD_LOGIN";

    /** 终端用户 WS 账号密码鉴权成功（每次 AUTH 成功 +1） */
    public static final String APP_USER_WS_LOGIN = "APP_USER_WS_LOGIN";
}
