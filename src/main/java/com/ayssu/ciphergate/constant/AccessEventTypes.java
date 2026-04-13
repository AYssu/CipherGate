package com.ayssu.ciphergate.constant;

/**
 * {@code access_event.event_type} 取值。
 */
public final class AccessEventTypes {

    private AccessEventTypes() {
    }

    /** 三方卡密 HTTP 登录（每次成功 +1） */
    public static final String CARD_LOGIN = "CARD_LOGIN";

    /**
     * 免费业务模式下的 HTTP 登录（不校验卡密；{@code ref_id} 固定为 0）
     */
    public static final String CARD_LOGIN_FREE = "CARD_LOGIN_FREE";

    /** 终端用户 WS 账号密码鉴权成功（每次 AUTH 成功 +1） */
    public static final String APP_USER_WS_LOGIN = "APP_USER_WS_LOGIN";
}
