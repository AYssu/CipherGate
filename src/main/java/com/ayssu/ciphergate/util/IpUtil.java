package com.ayssu.ciphergate.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtil {
    private IpUtil() {
    }

    public static String getIpAddr(HttpServletRequest request) {
        String ip = headerOrNull(request, "x-forwarded-for");
        if (ip == null) {
            ip = headerOrNull(request, "Proxy-Client-IP");
        }
        if (ip == null) {
            ip = headerOrNull(request, "WL-Proxy-Client-IP");
        }
        if (ip == null) {
            ip = headerOrNull(request, "X-Real-IP");
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        if (ip == null) {
            return "";
        }
        if (ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip.trim();
    }

    private static String headerOrNull(HttpServletRequest request, String key) {
        String v = request.getHeader(key);
        if (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v)) {
            return null;
        }
        return v;
    }
}

