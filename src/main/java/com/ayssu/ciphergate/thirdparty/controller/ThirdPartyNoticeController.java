package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.AppAnnouncementRequest;
import com.ayssu.ciphergate.thirdparty.dto.AppAnnouncementResponse;
import com.ayssu.ciphergate.thirdparty.dto.AppNoticeRequest;
import com.ayssu.ciphergate.thirdparty.dto.AppNoticeResponse;
import com.ayssu.ciphergate.thirdparty.exception.VersionOutOfRangeException;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 与 {@code /api/v1/card/login} 相同：{@link com.ayssu.ciphergate.thirdparty.auth.ThirdPartyAuthFilter} 验签与解密，
 * {@link com.ayssu.ciphergate.thirdparty.web.ThirdPartyResponseEncryptionAdvice} 加密响应。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方公告与更新", description = "与卡密登录相同鉴权与双向加密（/app/announcement 仅应用公告；/app/update-check 检查更新；/app/notice 为旧路径兼容）")
public class ThirdPartyNoticeController {

    private final ThirdPartyNoticeService thirdPartyNoticeService;

    @PostMapping("/app/announcement")
    @Operation(summary = "获取应用公告（仅 notice）")
    public Result<AppAnnouncementResponse> announcement(
            @RequestBody(required = false) AppAnnouncementRequest req,
            HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            return Result.success(thirdPartyNoticeService.getAnnouncementOnly(appId));
        } catch (Exception e) {
            log.warn("announcement failed: appId={}, msg={}", appId, e.getMessage());
            return Result.error("获取应用公告失败");
        }
    }

    /**
     * 正式路径 {@code /app/update-check}；{@code /app/notice} 保留兼容旧客户端（签名中的 PATH 须与实际请求路径一致）。
     */
    @PostMapping({"/app/update-check", "/app/notice"})
    @Operation(
            summary = "检查更新",
            description = "根据客户端 version 与后台 min/current 做区间校验；已对齐主线时返回应用公告 notice，落后时返回更新说明 updateNotice 及可选的 updateDownloadUrl（本服务 ticket 下载）"
    )
    public Result<AppNoticeResponse> updateCheck(@RequestBody(required = false) AppNoticeRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            return Result.success(thirdPartyNoticeService.getNotice(appId, req, http));
        } catch (VersionOutOfRangeException e) {
            log.warn("update-check version rejected: appId={}, msg={}", appId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.warn("update-check failed: appId={}, msg={}", appId, e.getMessage());
            return Result.error("检查更新失败");
        }
    }
}
