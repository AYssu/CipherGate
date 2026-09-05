package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.service.FunctionPluginModuleService;
import com.ayssu.ciphergate.service.PluginModuleService;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
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
    private final FunctionPluginModuleService functionPluginModuleService;
    private final FunctionRuntimeService functionRuntimeService;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 加载加密插件
        try {
            pluginModuleService.loadEnabledPluginsOnStartup();
        } catch (Exception e) {
            log.error("启动加载加密插件失败", e);
        }

        // 2. 加载函数插件
        try {
            functionPluginModuleService.loadEnabledPluginsOnStartup();
        } catch (Exception e) {
            log.error("启动加载函数插件失败", e);
        }

        // 3. 刷新函数插件注册表（扫描所有已加载的 FunctionPlugin 实现）
        try {
            functionRuntimeService.refresh();
        } catch (Exception e) {
            log.error("扫描函数插件失败", e);
        }
    }
}
