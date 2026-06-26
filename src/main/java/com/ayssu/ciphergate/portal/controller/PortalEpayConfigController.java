package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.portal.entity.ApplicationEpayConfig;
import com.ayssu.ciphergate.portal.mapper.ApplicationEpayConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/portal/epay-config")
@Tag(name = "门户支付配置", description = "应用创建者配置易支付 / 超管开启购买")
@RequiredArgsConstructor
public class PortalEpayConfigController {

    private final ApplicationEpayConfigMapper epayConfigMapper;
    private final ApplicationMapper applicationMapper;

    @GetMapping("/{appId}")
    @Operation(summary = "获取应用易支付配置")
    public Result<ApplicationEpayConfig> getConfig(@PathVariable Long appId) {
        ApplicationEpayConfig config = epayConfigMapper.selectOne(
            new LambdaQueryWrapper<ApplicationEpayConfig>()
                .eq(ApplicationEpayConfig::getAppId, appId)
        );
        return Result.success(config);
    }

    @PostMapping("/{appId}")
    @Operation(summary = "保存应用易支付配置")
    public Result<Void> saveConfig(@PathVariable Long appId, @RequestBody Map<String, String> body) {
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
    @Operation(summary = "超级管理员开启/关闭应用购买功能")
    public Result<Void> togglePayment(@PathVariable Long appId, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            return Result.badRequest("应用不存在");
        }

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

        log.info("应用购买功能状态变更: appId={}, enabled={}", appId, enabled);
        return Result.success(enabled ? "已开启" : "已关闭", null);
    }
}
