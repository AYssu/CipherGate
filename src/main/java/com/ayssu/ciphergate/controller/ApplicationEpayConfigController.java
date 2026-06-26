package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.portal.entity.ApplicationEpayConfig;
import com.ayssu.ciphergate.portal.mapper.ApplicationEpayConfigMapper;
import com.ayssu.ciphergate.util.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/applications")
@Tag(name = "应用支付配置", description = "应用创建者配置易支付 / 超管开启购买")
@RequiredArgsConstructor
public class ApplicationEpayConfigController {

    private final ApplicationEpayConfigMapper epayConfigMapper;
    private final ApplicationMapper applicationMapper;

    @GetMapping("/{appId}/epay-config")
    @Operation(summary = "获取应用易支付配置")
    public Result<ApplicationEpayConfig> getConfig(@PathVariable Long appId) {
        Result<Void> access = checkAccess(appId);
        if (access != null) return Result.error(access.getCode(), access.getMessage());
        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
        );
        return Result.success(config);
    }

    @PostMapping("/{appId}/epay-config")
    @Operation(summary = "保存应用易支付配置")
    public Result<Void> saveConfig(@PathVariable Long appId, @RequestBody Map<String, String> body) {
        Result<Void> access = checkAccess(appId);
        if (access != null) return Result.error(access.getCode(), access.getMessage());

        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
        );

        if (config == null) {
            config = new ApplicationEpayConfig();
            config.setAppId(appId);
            config.setEnabled(false);
        }

        config.setEpayUrl(body.get("epayUrl"));
        config.setEpayPid(body.get("epayPid"));
        config.setEpayKey(body.get("epayKey"));
        config.setNotifyUrl(body.get("notifyUrl"));
        config.setReturnUrl(body.get("returnUrl"));

        if (config.getId() == null) {
            epayConfigMapper.insert(config);
        } else {
            epayConfigMapper.updateById(config);
        }

        log.info("保存应用易支付配置: appId={}", appId);
        return Result.success("保存成功", null);
    }

    @PostMapping("/{appId}/toggle-payment")
    @Operation(summary = "开启/关闭应用购买功能（仅超管）")
    public Result<Void> togglePayment(@PathVariable Long appId, @RequestBody Map<String, Boolean> body) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) {
            return Result.error(401, "未登录");
        }

        // 仅超管可操作
        if (user.getRoles() == null || user.getRoles().stream()
                .noneMatch(r -> "SUPER_ADMIN".equals(r.getRoleCode()))) {
            return Result.error(403, "仅超级管理员可操作");
        }

        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            return Result.badRequest("应用不存在");
        }

        Boolean enabled = body.get("enabled");

        if (Boolean.TRUE.equals(enabled)) {
            ApplicationEpayConfig config = epayConfigMapper.selectOne(
                new LambdaQueryWrapper<ApplicationEpayConfig>()
                    .eq(ApplicationEpayConfig::getAppId, appId)
            );
            if (config == null || config.getEpayUrl() == null || config.getEpayPid() == null) {
                return Result.badRequest("请先配置易支付参数后再开启");
            }
        }

        app.setPortalPaymentEnabled(enabled);
        applicationMapper.updateById(app);

        // 同步更新 epay_config 的 enabled 状态
        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
        );
        if (config != null) {
            config.setEnabled(enabled);
            config.setUpdatedAt(java.time.LocalDateTime.now());
            epayConfigMapper.updateById(config);
        }

        log.info("应用购买功能状态变更: appId={}, enabled={}, operator={}", appId, enabled, user.getLogin());
        return Result.success(enabled ? "已开启" : "已关闭", null);
    }

    /**
     * 检查访问权限：超管可操作所有应用，普通用户只能操作自己的应用
     */
    private Result<Void> checkAccess(Long appId) {
        User user = AuthUtils.getCurrentUser();
        if (user == null) {
            return Result.error(401, "未登录");
        }

        // 超管可以操作所有应用
        if (user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getRoleCode()))) {
            return null;
        }

        // 普通用户只能操作自己的应用
        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            return Result.badRequest("应用不存在");
        }
        if (!app.getOwnerId().equals(user.getId())) {
            return Result.error(403, "无权操作此应用");
        }

        return null;
    }
}
