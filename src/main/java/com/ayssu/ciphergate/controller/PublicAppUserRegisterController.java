package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.dto.AppUserRegisterSendCodeRequest;
import com.ayssu.ciphergate.dto.AppUserRegisterSubmitRequest;
import com.ayssu.ciphergate.service.AppUserRegisterVerificationService;
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

/**
 * 应用终端用户自助注册（公开接口，无需登录）。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/app-user/register")
@RequiredArgsConstructor
@Validated
@Tag(name = "公开-应用用户注册", description = "自助注册发送验证码与提交")
public class PublicAppUserRegisterController {

    private final AppUserRegisterVerificationService registerVerificationService;

    @PostMapping("/send-email-code")
    @Operation(summary = "发送注册邮箱验证码", description = "验证码写入 Redis，3 分钟有效，仅用于注册；60 秒内不可重复发送同一邮箱")
    public Result<Void> sendEmailCode(@Valid @RequestBody AppUserRegisterSendCodeRequest body, HttpServletRequest request) {
        try {
            registerVerificationService.sendRegisterEmailVerificationCode(
                    body.getAppId(), body.getEmail(), resolveClientIp(request));
            return Result.success("验证码已发送，请查收邮件（3 分钟内有效）", null);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("邮件")) {
                return Result.error(e.getMessage());
            }
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("发送注册验证码异常", e);
            return Result.error("发送失败，请稍后重试");
        }
    }

    @PostMapping("/submit")
    @Operation(summary = "提交注册", description = "校验邮箱验证码（一次性）后创建终端用户；用户名用于登录")
    public Result<Void> submit(@Valid @RequestBody AppUserRegisterSubmitRequest body) {
        try {
            registerVerificationService.completeSelfRegister(
                    body.getAppId(), body.getUsername(), body.getEmail(), body.getEmailCode(), body.getPassword());
            return Result.success("注册成功", null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("自助注册提交异常", e);
            return Result.error("注册失败，请稍后重试");
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
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
