package com.ayssu.ciphergate.util;

import jakarta.servlet.http.HttpServletRequest;

/** Read only the trusted-proxy address normalized by Tomcat RemoteIpValve. */
public final class IpUtil {
    private IpUtil() {}
    public static String getIpAddr(HttpServletRequest request) {
        return request == null || request.getRemoteAddr() == null ? "" : request.getRemoteAddr().trim();
    }
}
