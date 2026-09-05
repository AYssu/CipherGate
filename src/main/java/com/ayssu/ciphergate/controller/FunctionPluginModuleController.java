package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.FunctionPluginModule;
import com.ayssu.ciphergate.service.FunctionPluginModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 函数插件模块管理控制器。
 * <p>
 * 提供 WebSocket 函数执行插件的上传、启停、配置等 REST API。
 */
@Slf4j
@RestController
@RequestMapping("/api/function-plugins")
@RequiredArgsConstructor
@Tag(name = "函数插件管理", description = "WebSocket 函数执行插件的上传、启停、配置相关接口")
public class FunctionPluginModuleController {

    private final FunctionPluginModuleService functionPluginModuleService;

    @PostMapping("/upload")
    @RequirePermission("PLUGIN_UPLOAD")
    @Operation(summary = "上传函数插件", description = "上传函数插件 JAR 包并注册插件元信息")
    public Result<FunctionPluginModule> uploadPlugin(@RequestPart("file") MultipartFile file,
                                                     @RequestParam(required = false) String pluginId,
                                                     @RequestParam(required = false) String pluginVersion,
                                                     @RequestParam(required = false) String pluginName,
                                                     @RequestParam(required = false) String remark) {
        try {
            FunctionPluginModule plugin = functionPluginModuleService.uploadPlugin(file, pluginId, pluginName, pluginVersion, remark);
            return Result.success("函数插件上传成功", plugin);
        } catch (Exception e) {
            log.error("函数插件上传失败", e);
            return Result.error("函数插件上传失败: " + e.getMessage());
        }
    }

    @GetMapping
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "查询函数插件列表")
    public Result<List<FunctionPluginModule>> listPlugins() {
        try {
            return Result.success(functionPluginModuleService.listPlugins());
        } catch (Exception e) {
            log.error("查询函数插件列表失败", e);
            return Result.error("查询函数插件列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    @RequirePermission("PLUGIN_ENABLE")
    @Operation(summary = "启用函数插件")
    public Result<String> enablePlugin(@PathVariable Long id) {
        try {
            functionPluginModuleService.enablePlugin(id);
            return Result.success("函数插件启用成功", "OK");
        } catch (Exception e) {
            log.error("启用函数插件失败", e);
            return Result.error("启用函数插件失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/disable")
    @RequirePermission("PLUGIN_DISABLE")
    @Operation(summary = "停用函数插件")
    public Result<String> disablePlugin(@PathVariable Long id) {
        try {
            functionPluginModuleService.disablePlugin(id);
            return Result.success("函数插件停用成功", "OK");
        } catch (Exception e) {
            log.error("停用函数插件失败", e);
            return Result.error("停用函数插件失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission("PLUGIN_DELETE")
    @Operation(summary = "删除函数插件")
    public Result<String> deletePlugin(@PathVariable Long id) {
        try {
            functionPluginModuleService.deletePlugin(id);
            return Result.success("函数插件删除成功", "OK");
        } catch (Exception e) {
            log.error("删除函数插件失败", e);
            return Result.error("删除函数插件失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/config-schema")
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "获取函数插件配置结构")
    public Result<Map<String, Object>> getConfigSchema(@PathVariable Long id) {
        try {
            return Result.success(functionPluginModuleService.getPluginConfigSchema(id));
        } catch (Exception e) {
            log.error("查询函数插件配置Schema失败", e);
            return Result.error("查询函数插件配置Schema失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/config")
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "获取函数插件配置")
    public Result<Map<String, Object>> getConfig(@PathVariable Long id) {
        try {
            return Result.success(functionPluginModuleService.getPluginConfig(id));
        } catch (Exception e) {
            log.error("查询函数插件配置失败", e);
            return Result.error("查询函数插件配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/config")
    @RequirePermission("PLUGIN_ENABLE")
    @Operation(summary = "更新函数插件配置")
    public Result<String> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> configValues) {
        try {
            functionPluginModuleService.updatePluginConfig(id, configValues);
            return Result.success("函数插件配置保存成功", "OK");
        } catch (Exception e) {
            log.error("保存函数插件配置失败", e);
            return Result.error("保存函数插件配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/test")
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "测试函数执行", description = "直接测试函数执行，不需要 WebSocket 连接")
    public Result<Map<String, Object>> testFunction(@RequestBody Map<String, Object> request) {
        try {
            String pluginId = (String) request.get("pluginId");
            String funcName = (String) request.get("func");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

            if (pluginId == null || funcName == null) {
                return Result.error("缺少 pluginId 或 func 参数");
            }

            com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult result =
                    functionPluginModuleService.testFunction(pluginId, funcName, params);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("success", result.success());
            response.put("data", result.data());
            response.put("code", result.code());
            response.put("message", result.message());
            return Result.success(response);
        } catch (Exception e) {
            log.error("测试函数执行失败", e);
            return Result.error("测试函数执行失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/functions")
    @RequirePermission("PLUGIN_LIST")
    @Operation(summary = "获取插件函数列表详情", description = "获取插件提供的函数列表及参数说明")
    public Result<List<Map<String, Object>>> getFunctions(@PathVariable Long id) {
        try {
            return Result.success(functionPluginModuleService.getPluginFunctions(id));
        } catch (Exception e) {
            log.error("获取函数列表失败", e);
            return Result.error("获取函数列表失败: " + e.getMessage());
        }
    }
}
