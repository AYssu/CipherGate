package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.*;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyCardService;
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
@Tag(name = "三方卡密登录", description = "第三方平台基于 appKey/appSecret 的卡密登录接口")
public class ThirdPartyCardController {
    private final ThirdPartyCardService thirdPartyCardService;

    @PostMapping("/card/login")
    @Operation(summary = "卡密登录")
    public Result<CardLoginResponse> login(@RequestBody CardLoginRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            String clientIp = IpUtil.getIpAddr(http);
            return Result.success(thirdPartyCardService.login(appId, req, clientIp));
        } catch (Exception e) {
            log.warn("login failed: appId={}, msg={}", appId, e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}

