package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyCardService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyCardRateLimitService;
import com.ayssu.ciphergate.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方卡密", description = "第三方基于 appKey/appSecret 的卡密登录与换绑等设备类接口")
public class ThirdPartyCardController {
    private final ThirdPartyCardService thirdPartyCardService;
    private final ThirdPartyCardRateLimitService thirdPartyCardRateLimitService;

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
}

