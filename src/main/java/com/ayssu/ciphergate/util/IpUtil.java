package com.ayssu.ciphergate.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 * <p>
 * 只读取 Spring Boot RemoteIpValve 规范化后的 remoteAddr，避免客户端伪造
 * X-Forwarded-For/X-Real-IP 影响限流、白名单和审计。
 */
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
        String value = request.getHeader(key);
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }
}
