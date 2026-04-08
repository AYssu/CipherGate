package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.PluginModule;
import com.ayssu.ciphergate.service.PluginModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginModuleController {

    private final PluginModuleService pluginModuleService;

    @PostMapping("/upload")
    @RequirePermission("PLUGIN_UPLOAD")
    public Result<PluginModule> uploadPlugin(@RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) String pluginId,
                                             @RequestParam(required = false) String pluginVersion,
                                             @RequestParam(required = false) String pluginName,
                                             @RequestParam(required = false) String remark) {
        try {
            PluginModule plugin = pluginModuleService.uploadPlugin(file, pluginId, pluginName, pluginVersion, remark);
            return Result.success("插件上传成功", plugin);
        } catch (Exception e) {
            log.error("插件上传失败", e);
            return Result.error("插件上传失败: " + e.getMessage());
        }
    }

    @GetMapping
    @RequirePermission("PLUGIN_LIST")
    public Result<List<PluginModule>> listPlugins() {
        try {
            return Result.success(pluginModuleService.listPlugins());
        } catch (Exception e) {
            log.error("查询插件列表失败", e);
            return Result.error("查询插件列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    @RequirePermission("PLUGIN_ENABLE")
    public Result<String> enablePlugin(@PathVariable Long id) {
        try {
            pluginModuleService.enablePlugin(id);
            return Result.success("插件启用成功", "OK");
        } catch (Exception e) {
            log.error("启用插件失败", e);
            return Result.error("启用插件失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/disable")
    @RequirePermission("PLUGIN_DISABLE")
    public Result<String> disablePlugin(@PathVariable Long id) {
        try {
            pluginModuleService.disablePlugin(id);
            return Result.success("插件停用成功", "OK");
        } catch (Exception e) {
            log.error("停用插件失败", e);
            return Result.error("停用插件失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission("PLUGIN_DELETE")
    public Result<String> deletePlugin(@PathVariable Long id) {
        try {
            pluginModuleService.deletePlugin(id);
            return Result.success("插件删除成功", "OK");
        } catch (Exception e) {
            log.error("删除插件失败", e);
            return Result.error("删除插件失败: " + e.getMessage());
        }
    }
}
