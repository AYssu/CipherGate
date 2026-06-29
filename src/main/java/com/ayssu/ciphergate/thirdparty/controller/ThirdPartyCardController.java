package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyCardService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyCardRateLimitService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyHeartbeatService;
import com.ayssu.ciphergate.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方卡密", description = "第三方基于 appKey/appSecret 的卡密登录与换绑等设备类接口")
public class ThirdPartyCardController {
    private final ThirdPartyCardService thirdPartyCardService;
    private final ThirdPartyCardRateLimitService thirdPartyCardRateLimitService;
    private final ThirdPartyHeartbeatService thirdPartyHeartbeatService;

    @PostMapping("/card/login")
    @Operation(summary = "卡密登录")
    public Result<CardLoginResponse> login(@RequestBody CardLoginRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        String clientIp = IpUtil.getIpAddr(http);
        try {
            thirdPartyCardRateLimitService.checkBeforeLogin(appId, clientIp, req == null ? null : req.getCardCode());
            CardLoginResponse resp = thirdPartyCardService.login(appId, req, clientIp);
            thirdPartyCardRateLimitService.markResult(appId, clientIp, true);
            return Result.success(resp);
        } catch (Exception e) {
            thirdPartyCardRateLimitService.markResult(appId, clientIp, false);
            log.warn("login failed: appId={}, msg={}", appId, e.getMessage());
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = "登录失败";
            }
            return Result.error(msg);
        }
    }

    @PostMapping("/card/rebind")
    @Operation(summary = "卡密换绑设备")
    public Result<CardRebindResponse> rebind(@RequestBody CardRebindRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        String clientIp = IpUtil.getIpAddr(http);
        try {
            thirdPartyCardRateLimitService.checkBeforeLogin(appId, clientIp, req == null ? null : req.getCardCode());
            CardRebindResponse resp = thirdPartyCardService.rebindDevice(appId, req);
            thirdPartyCardRateLimitService.markResult(appId, clientIp, true);
            return Result.success(resp);
        } catch (Exception e) {
            thirdPartyCardRateLimitService.markResult(appId, clientIp, false);
            log.warn("rebind failed: appId={}, msg={}", appId, e.getMessage());
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = "换绑失败";
            }
            return Result.error(msg);
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> heartbeatFailures = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_FAILURES_BEFORE_401 = 3;

    @PostMapping("/card/heartbeat")
    @Operation(summary = "卡密心跳", description = "校验心跳 token，返回新交换 token + 应用变量；最快 30 秒一次")
    public ResponseEntity<Result<HeartbeatResponse>> heartbeat(@RequestBody HeartbeatRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return ResponseEntity.status(401).body(Result.unauthorized("未识别应用"));
        }
        String token = req.getToken();
        try {
            HeartbeatResponse resp = thirdPartyHeartbeatService.exchange(token);
            // 成功，清除该 token 失败计数
            heartbeatFailures.remove(token);
            return ResponseEntity.ok(Result.success(resp));
        } catch (Exception e) {
            log.warn("heartbeat failed: appId={}, msg={}", appId, e.getMessage());
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = "心跳失败";
            }
            // token 无效/过期，按 token 累计失败次数
            if (msg.contains("token") || msg.contains("过期") || msg.contains("无效")) {
                int failures = heartbeatFailures
                        .computeIfAbsent(token, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                        .incrementAndGet();
                if (failures >= MAX_FAILURES_BEFORE_401) {
                    log.warn("heartbeat 401: appId={}, token={}... 连续失败{}次，拒绝重试", appId, token.substring(0, Math.min(8, token.length())), failures);
                    return ResponseEntity.status(401).body(Result.unauthorized(msg));
                }
                // 未达阈值，返回 200 + 错误信息，给客户端机会
                return ResponseEntity.ok(Result.error(msg));
            }
            return ResponseEntity.status(500).body(Result.error(msg));
        }
    }
}

