package com.ayssu.ciphergate.thirdparty.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.auth.ThirdPartyHeaders;
import com.ayssu.ciphergate.thirdparty.dto.AppVariablesRequest;
import com.ayssu.ciphergate.thirdparty.dto.AppVariablesResponse;
import com.ayssu.ciphergate.thirdparty.service.ThirdPartyAppVariableService;
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
 * 与 {@code /api/v1/card/login} 相同鉴权与双向加密。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "三方应用变量", description = "与卡密登录相同鉴权与加密；返回启用变量键值")
public class ThirdPartyVariableController {

    private final ThirdPartyAppVariableService thirdPartyAppVariableService;

    @PostMapping("/app/variables")
    @Operation(summary = "查询应用变量")
    public Result<AppVariablesResponse> variables(
            @RequestBody(required = false) AppVariablesRequest req,
            HttpServletRequest http) {
        Long appId = (Long) http.getAttribute(ThirdPartyHeaders.ATTR_APPLICATION_ID);
        if (appId == null) {
            return Result.unauthorized("未识别应用");
        }
        try {
            AppVariablesResponse out = new AppVariablesResponse();
            out.setVariables(thirdPartyAppVariableService.getEnabledVariablesMap(appId));
            return Result.success(out);
        } catch (Exception e) {
            log.warn("variables query failed: appId={}, msg={}", appId, e.getMessage());
            return Result.error("查询应用变量失败");
        }
    }
}
