package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.PublicLicenseQueryRequest;
import com.ayssu.ciphergate.dto.PublicLicenseQueryResponse;
import com.ayssu.ciphergate.dto.PublicLicenseUnbindRequest;
import com.ayssu.ciphergate.service.PublicLicenseSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用卡密自助查询与解绑（公开接口，无需登录）。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/license")
@RequiredArgsConstructor
@Validated
@Tag(name = "公开-卡密自助", description = "自助查询卡密剩余时间与解绑")
public class PublicLicenseSelfServiceController {

    private final PublicLicenseSelfService publicLicenseSelfService;
    private final com.ayssu.ciphergate.service.PublicChallengeService challenges;

    @PostMapping("/query-remaining")
    @Operation(summary = "查询卡密剩余到期时间")
    public Result<PublicLicenseQueryResponse> queryRemaining(@Valid @RequestBody PublicLicenseQueryRequest body, jakarta.servlet.http.HttpServletRequest http) {
        challenges.verify("license-query", com.ayssu.ciphergate.util.IpUtil.getIpAddr(http), body.getAppId() + ":" + body.getKeyCode().trim().toUpperCase(java.util.Locale.ROOT), body);
        try {
            return Result.success(publicLicenseSelfService.queryRemaining(body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("公开卡密查询异常: errorType={}", e.getClass().getSimpleName());
            return Result.error("查询失败，请稍后重试");
        }
    }

    @PostMapping("/unbind")
    @Operation(summary = "解绑卡密绑定（设备/IP）")
    public Result<Void> unbind(@Valid @RequestBody PublicLicenseUnbindRequest body, jakarta.servlet.http.HttpServletRequest http) {
        challenges.verify("license-unbind", com.ayssu.ciphergate.util.IpUtil.getIpAddr(http), body.getAppId() + ":" + body.getKeyCode().trim().toUpperCase(java.util.Locale.ROOT), body);
        try {
            publicLicenseSelfService.unbind(body);
            return Result.success("解绑成功", null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("公开卡密解绑异常: errorType={}", e.getClass().getSimpleName());
            return Result.error("解绑失败，请稍后重试");
        }
    }
}
