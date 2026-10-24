package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginRequest;
import com.ayssu.ciphergate.thirdparty.dto.UserLoginResponse;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUserLoginException;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUserRateLimitService;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyUserService;
import com.ayssu.ciphergate.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方用户", description = "与卡密相同的应用签名及插件加密，不依赖 WS 的账号密码登录")
public class ThirdPartyUserController {
    private final ThirdPartyUserService userService;
    private final ThirdPartyUserRateLimitService rateLimiter;

    @PostMapping("/user/login")
    @Operation(summary = "用户账号密码登录", description = "用户名或邮箱+密码+设备标识；检查会员及设备，不创建在线会话或心跳 token")
    public Result<UserLoginResponse> login(@RequestBody UserLoginRequest request, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            String ip = IpUtil.getIpAddr(http);
            rateLimiter.checkBeforeLogin(appId, ip, request == null ? null : request.getUsername());
            return Result.success(userService.login(appId, request, ip));
        } catch (ThirdPartyUserLoginException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            // 不打印请求、密码、异常消息或 SQL 参数，避免敏感信息进入日志/响应。
            log.error("HTTP user login failed: appId={}, errorType={}", appId, e.getClass().getSimpleName());
            return Result.error("登录服务暂不可用，请稍后重试");
        }
    }
}
