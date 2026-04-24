package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.OpenTrialRequest;
import com.ayssu.ciphergate.service.OpenTrialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/open")
@Tag(name = "开放-试用接口", description = "第三方开放试用申请接口")
public class OpenTrialController {

    private final OpenTrialService openTrialService;

    @PostMapping("/trial")
    @Operation(summary = "申请试用", description = "当应用开启试用时，按 email + pid 为指定终端用户发放 1 天试用，且一个用户仅可申请一次")
    public Result<OpenTrialService.TrialApplyResult> applyTrial(@Valid @RequestBody OpenTrialRequest request,
                                                                HttpServletRequest httpRequest) {
        try {
            OpenTrialService.TrialApplyResult result = openTrialService.applyTrial(
                    request,
                    resolveClientIp(httpRequest),
                    httpRequest == null ? null : httpRequest.getHeader("User-Agent")
            );
            return Result.success("试用申请成功", result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        } catch (Exception e) {
            log.error("开放试用接口异常, pid={}, email={}", request.getPid(), request.getEmail(), e);
            return Result.error("试用申请失败，请稍后重试");
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
