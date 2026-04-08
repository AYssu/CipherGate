package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.service.TextProviderRuntimeService;
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
public class PluginTestController {

    private final TextProviderRuntimeService textProviderRuntimeService;

    /**
     * 默认返回 hello world。
     * 指定 pluginId 后可命中插件实现。
     */
    @GetMapping("/text")
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
