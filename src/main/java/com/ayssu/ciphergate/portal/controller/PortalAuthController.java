package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.portal.dto.*;
import com.ayssu.ciphergate.portal.service.PortalAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import com.ayssu.ciphergate.util.IpUtil;
@Slf4j
@RestController
@RequestMapping("/api/portal/auth")
@Tag(name = "门户认证", description = "终端用户登录、验证码、密码找回")
@RequiredArgsConstructor
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码")
    public Result<Map<String, Object>> getCaptcha(HttpServletRequest request) throws IOException {
        Map<String, Object> captcha = portalAuthService.generateCaptcha(resolveClientIp(request));
        return Result.success(captcha);
    }

    @PostMapping("/login")
    @Operation(summary = "终端用户登录")
    public Result<PortalLoginResponse> login(
            @Valid @RequestBody PortalLoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            String clientIp = resolveClientIp(httpRequest);
            PortalLoginResponse response = portalAuthService.login(request, clientIp);
            return Result.success("登录成功", response);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/select-app")
    @Operation(summary = "选择应用")
    public Result<String> selectApp(
            @RequestParam Long appId,
            HttpServletRequest httpRequest) {
        try {
            String email = getCurrentEmail();
            if (email == null) {
                return Result.error(401, "未登录");
            }
            String clientIp = resolveClientIp(httpRequest);
            String token = portalAuthService.selectApp(appId, email, getGrants(), clientIp);
            return Result.success("选择成功", token);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/recovery/send-code")
    @Operation(summary = "发送密码重置验证码")
    public Result<Void> sendRecoveryCode(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return Result.badRequest("邮箱不能为空");
            }
            portalAuthService.sendRecoveryCode(email, resolveClientIp(request), body.get("captchaId"), body.get("captchaCode"));
            return Result.success("验证码已发送", null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/recovery/apps")
    @Operation(summary = "根据邮箱查询关联的应用列表（密码找回用）")
    public Result<List<Map<String, Object>>> getRecoveryApps(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return Result.badRequest("邮箱不能为空");
            }
            return Result.success(portalAuthService.getAppsByEmail(email, body.get("verifyCode"), resolveClientIp(request)));
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/recovery/reset-password")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@Valid @RequestBody PortalPasswordRecoveryRequest request, HttpServletRequest httpRequest) {
        try {
            portalAuthService.resetPassword(request, resolveClientIp(httpRequest));
            return Result.success("密码重置成功", null);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/verify-email-code")
    @Operation(summary = "发送邮箱验证码（设置页面用）")
    public Result<Void> sendEmailVerifyCode(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        try {
            String email = body.get("email");
            if (email == null || email.isBlank()) {
                return Result.badRequest("邮箱不能为空");
            }
            if (getCurrentUserId() == null || getCurrentAppId() == null || getCurrentAppId() <= 0) return Result.error(401, "请先登录");
            portalAuthService.sendEmailVerifyCode(getCurrentUserId(), email, resolveClientIp(request));
            return Result.success("验证码已发送", null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        return normalizeIp(IpUtil.getIpAddr(request));
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isEmpty()) return ip;
        // IPv6 mapped IPv4: /0:0:0:0:0:0:0:1 or /::ffff:127.0.0.1
        if (ip.startsWith("/")) ip = ip.substring(1);
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) return "127.0.0.1";
        if (ip.startsWith("0:0:0:0:0:ffff:")) {
            String v4 = ip.substring("0:0:0:0:0:ffff:".length());
            if (v4.contains(":")) {
                // Full IPv6 with port-like format, extract last part
                int lastColon = v4.lastIndexOf(':');
                if (lastColon > 0) v4 = v4.substring(0, lastColon);
            }
            return v4;
        }
        if (ip.startsWith("::ffff:")) {
            String v4 = ip.substring(7);
            if (!v4.contains(":")) return v4;
        }
        // If it looks like pure IPv4, return as is
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte[] raw = addr.getAddress();
            if (raw.length == 4) return ip;
            // IPv6 - try to extract embedded IPv4
            if (raw.length == 16 && raw[10] == (byte) 0xff && raw[11] == (byte) 0xff) {
                return String.format("%d.%d.%d.%d", raw[12] & 0xff, raw[13] & 0xff, raw[14] & 0xff, raw[15] & 0xff);
            }
        } catch (Exception ignored) {
        }
        return ip;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            return (Map<String, Object>) auth.getPrincipal();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> getGrants() {
        Map<String, Object> p = getPrincipal();
        return p != null && p.get("grants") instanceof Map ? (Map<String, ?>) p.get("grants") : Map.of();
    }

    private static String getCurrentEmail() {
        Map<String, Object> p = getPrincipal();
        return p != null ? (String) p.get("email") : null;
    }

    private static Long getCurrentUserId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("id")).longValue() : null;
    }

    private static Long getCurrentAppId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("appId")).longValue() : null;
    }
}
