package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.service.TextProviderRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/plugin-test")
@RequiredArgsConstructor
@Tag(name = "插件测试", description = "插件运行时能力验证接口")
public class PluginTestController {

    private final TextProviderRuntimeService textProviderRuntimeService;

    /**
     * 默认返回 hello world。
     * 指定 pluginId 后可命中插件实现。
     */
    @GetMapping("/text")
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "获取插件文本输出", description = "不传 pluginId 时使用自动覆盖策略，传 pluginId 时调用指定插件实现")
    public Result<Map<String, String>> getText(@RequestParam(required = false) String pluginId) {
        try {
            boolean useAutoOverride = (pluginId == null || pluginId.isBlank());
            String text = useAutoOverride ? textProviderRuntimeService.getText() : textProviderRuntimeService.getText(pluginId);
            Map<String, String> data = new HashMap<>();
            data.put("pluginId", useAutoOverride ? "AUTO_OVERRIDE" : pluginId);
            data.put("text", text);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取插件文本失败", e);
            return Result.error("获取插件文本失败: " + e.getMessage());
        }
    }
}
