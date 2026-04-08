package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.PluginModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PluginStartupLoader implements ApplicationRunner {

    private final PluginModuleService pluginModuleService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            pluginModuleService.loadEnabledPluginsOnStartup();
        } catch (Exception e) {
            log.error("启动加载插件失败", e);
        }
    }
}
