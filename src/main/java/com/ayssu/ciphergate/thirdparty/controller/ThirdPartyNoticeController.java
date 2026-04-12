package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
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
@Tag(name = "三方软件公告", description = "与卡密登录相同鉴权与双向加密的公告查询")
public class ThirdPartyNoticeController {

    private final ThirdPartyNoticeService thirdPartyNoticeService;

    @PostMapping("/app/notice")
    @Operation(summary = "获取软件公告")
    public Result<AppNoticeResponse> notice(@RequestBody(required = false) AppNoticeRequest req, HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            return Result.success(thirdPartyNoticeService.getNotice(appId, req, http));
        } catch (VersionOutOfRangeException e) {
            log.warn("notice version rejected: appId={}, msg={}", appId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.warn("notice failed: appId={}, msg={}", appId, e.getMessage());
            return Result.error("获取公告失败");
        }
    }
}
