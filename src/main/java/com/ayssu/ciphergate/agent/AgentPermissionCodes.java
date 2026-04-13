package com.ayssu.ciphergate.agent;

/**
 * 代理权限项编码（独立于 RBAC permissions 表，按 app_agent_permission 存储）。
 */
public final class AgentPermissionCodes {
    private AgentPermissionCodes() {
    }

    // License
    public static final String LICENSE_LIST = "LICENSE_LIST";
    public static final String LICENSE_CREATE = "LICENSE_CREATE";
    public static final String LICENSE_UPDATE = "LICENSE_UPDATE";
    public static final String LICENSE_DELETE = "LICENSE_DELETE";
    /** 允许查看应用内全部数据（否则按 OWN_ONLY 过滤） */
    public static final String LICENSE_VIEW_ALL = "LICENSE_VIEW_ALL";

    // AppUser
    public static final String APP_USER_LIST = "APP_USER_LIST";
    public static final String APP_USER_CREATE = "APP_USER_CREATE";
    public static final String APP_USER_UPDATE = "APP_USER_UPDATE";
    public static final String APP_USER_DELETE = "APP_USER_DELETE";
    public static final String APP_USER_VIEW_ALL = "APP_USER_VIEW_ALL";
}

