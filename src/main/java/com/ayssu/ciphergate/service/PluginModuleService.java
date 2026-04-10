package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.PluginModule;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface PluginModuleService {
    PluginModule uploadPlugin(MultipartFile file, String pluginId, String pluginName, String pluginVersion, String remark);

    List<PluginModule> listPlugins();

    void enablePlugin(Long id);

    void disablePlugin(Long id);

    void deletePlugin(Long id);

    void loadEnabledPluginsOnStartup();

    Map<String, Object> getPluginConfigSchema(Long id);

    Map<String, Object> getPluginConfig(Long id);

    void updatePluginConfig(Long id, Map<String, Object> configValues);

    /**
     * 按逻辑 pluginId（与 {@code Application.encryptionPlugin} / PF4J {@code plugin.id} 一致）从库表 {@code plugin_module.config_values} 解析 JSON。
     * 优先取已启用（status=1）记录，否则取同 pluginId 下最近更新的一条。
     */
    Map<String, Object> resolveRuntimeConfigValues(String pluginId);
}
