package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.PasswordLoginRequest;
import com.ayssu.ciphergate.dto.SetPasswordRequest;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.ActivityLogService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.LoginRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 密码登录接口
 * <p>
 * 作为 GitHub OAuth 登录的降级方案，解决网络不佳时无法登录的问题。
 * 注册仅通过 GitHub OAuth 完成，密码登录仅作为已注册用户的备选登录方式。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "密码认证", description = "密码登录接口")
@RequiredArgsConstructor
public class PasswordLoginController {

    private final UserService userService;
    private final ActivityLogService activityLogService;
    private final LoginRateLimiter loginRateLimiter;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 密码登录（使用 GitHub 账号名 + 密码）
     */
    @PostMapping("/login")
    @Operation(summary = "密码登录")
    public Result<Map<String, Object>> login(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {

        log.info("密码登录尝试: login={}", request.getLogin());

        // 限流检查：账号级别
        String accountKey = "account:" + request.getLogin();
        long accountLockSeconds = loginRateLimiter.getLockRemainingSeconds(accountKey);
        if (accountLockSeconds > 0) {
            log.warn("密码登录被限流: login={}, 剩余锁定{}秒", request.getLogin(), accountLockSeconds);
            return Result.error(429, "密码登录尝试次数过多，请 " + (accountLockSeconds / 60) + " 分钟后重试，或使用 GitHub 授权登录");
        }

        // 限流检查：IP 级别
        String ipKey = "ip:" + getClientIpAddress(httpRequest);
        long ipLockSeconds = loginRateLimiter.getLockRemainingSeconds(ipKey);
        if (ipLockSeconds > 0) {
            log.warn("密码登录被限流: ip={}, 剩余锁定{}秒", getClientIpAddress(httpRequest), ipLockSeconds);
            return Result.error(429, "登录尝试次数过多，请 " + (ipLockSeconds / 60) + " 分钟后重试，或使用 GitHub 授权登录");
        }

        // 查找用户
        User user = userService.findByLogin(request.getLogin());
        if (user == null) {
            loginRateLimiter.recordFailure(accountKey);
            loginRateLimiter.recordFailure(ipKey);
            log.warn("密码登录失败: 用户不存在 login={}", request.getLogin());
            return Result.error(401, "用户名或密码错误");
        }

        // 校验密码
        if (user.getPassword() == null) {
            loginRateLimiter.recordFailure(accountKey);
            loginRateLimiter.recordFailure(ipKey);
            log.warn("密码登录失败: 用户未设置密码 login={}", request.getLogin());
            return Result.error(401, "该账号未设置密码，请使用 GitHub 登录后设置密码");
        }

        if (!ENCODER.matches(request.getPassword(), user.getPassword())) {
            loginRateLimiter.recordFailure(accountKey);
            loginRateLimiter.recordFailure(ipKey);
            log.warn("密码登录失败: 密码错误 login={}", request.getLogin());
            return Result.error(401, "用户名或密码错误");
        }

        // 检查账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("密码登录失败: 账号已禁用 login={}", request.getLogin());
            return Result.error(403, "账号已被禁用");
        }

        // 加载角色权限
        User fullUser = userService.getUserWithRolesAndPermissions(user.getId());
        if (fullUser.getRoles() == null || fullUser.getRoles().isEmpty()) {
            log.warn("用户 {} 没有角色，无法登录", request.getLogin());
            return Result.error(403, "用户无角色权限");
        }

        // 构建权限列表
        var authorities = fullUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()))
                .collect(Collectors.toList());

        // 创建认证令牌
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(fullUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 存入 Session
        // "user" 供业务层读取用户信息
        // "passwordAuth" 供 ActiveUserSessionFilter 在下次请求时恢复 SecurityContext
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("user", fullUser);
        session.setAttribute("passwordAuth", authToken);
        session.setMaxInactiveInterval(7 * 24 * 60 * 60); // 7天

        log.info("密码登录成功: userId={}, login={}", fullUser.getId(), fullUser.getLogin());

        // 登录成功，清除失败记录
        loginRateLimiter.clearFailures(accountKey);
        loginRateLimiter.clearFailures(ipKey);

        // 记录登录日志
        try {
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            activityLogService.log(
                    fullUser.getId(), fullUser.getName(),
                    "LOGIN", "AUTHENTICATION",
                    "通过密码登录",
                    ipAddress, userAgent, "SUCCESS"
            );
        } catch (Exception e) {
            log.error("记录登录日志失败: {}", e.getMessage());
        }

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("message", "登录成功");
        data.put("userId", fullUser.getId());
        data.put("login", fullUser.getLogin());
        data.put("name", fullUser.getName());
        data.put("avatarUrl", fullUser.getAvatarUrl());

        return Result.success("登录成功", data);
    }

    /**
     * 设置密码（已登录的 OAuth 用户设置本地密码）
     */
    @PostMapping("/set-password")
    @Operation(summary = "设置密码（已登录用户）")
    public Result<Void> setPassword(
            @Valid @RequestBody SetPasswordRequest request,
            HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Result.error(401, "未登录");
        }

        user.setPassword(ENCODER.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);

        // 更新 Session 中的用户信息
        session.setAttribute("user", user);

        log.info("用户 {} 设置了密码", user.getLogin());
        return Result.success("密码设置成功", null);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
