package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.portal.dto.PortalEmailChangeRequest;
import com.ayssu.ciphergate.portal.dto.PortalPasswordChangeRequest;
import com.ayssu.ciphergate.portal.service.PortalSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/portal/settings")
@Tag(name = "门户设置", description = "终端用户账号设置")
@RequiredArgsConstructor
public class PortalSettingsController {

    private final PortalSettingsService settingsService;

    @GetMapping("/profile")
    @Operation(summary = "获取个人资料")
    public Result<Map<String, Object>> getProfile() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        Map<String, Object> profile = settingsService.getProfile(userId);
        return Result.success(profile);
    }

    @PutMapping("/nickname")
    @Operation(summary = "更新昵称")
    public Result<Void> updateNickname(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return Result.badRequest("昵称不能为空");
        }
        settingsService.updateNickname(userId, nickname);
        return Result.success("昵称更新成功", null);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody PortalPasswordChangeRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        try {
            settingsService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
            return Result.success("密码修改成功", null);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PutMapping("/email")
    @Operation(summary = "更换邮箱")
    public Result<Void> changeEmail(@Valid @RequestBody PortalEmailChangeRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        try {
            settingsService.changeEmail(userId, request.getCurrentPassword(), request.getNewEmail(), request.getVerifyCode());
            return Result.success("邮箱更换成功", null);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            return (Map<String, Object>) auth.getPrincipal();
        }
        return null;
    }

    private static Long getCurrentUserId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("id")).longValue() : null;
    }
}
