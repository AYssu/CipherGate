package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.PluginModule;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PluginModuleService {
    PluginModule uploadPlugin(MultipartFile file, String pluginId, String pluginName, String pluginVersion, String remark);

    List<PluginModule> listPlugins();

    void enablePlugin(Long id);

    void disablePlugin(Long id);

    void deletePlugin(Long id);

    void loadEnabledPluginsOnStartup();
}
