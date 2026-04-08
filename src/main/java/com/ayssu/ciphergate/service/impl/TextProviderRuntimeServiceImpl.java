package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.plugin.TextProvider;
import com.ayssu.ciphergate.service.TextProviderRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextProviderRuntimeServiceImpl implements TextProviderRuntimeService {

    private final List<TextProvider> localProviders;
    private final PluginManager pluginManager;

    @Override
    public String getText() {
        // 接口级替换：有插件实现就直接接管，无需业务层显式留口子
        List<TextProvider> extensions = pluginManager.getExtensions(TextProvider.class);
        log.info("TextProvider自动模式: pluginExtensionsCount={}, localProvidersCount={}",
                extensions.size(), localProviders.size());
        if (!extensions.isEmpty()) {
            for (TextProvider extension : extensions) {
                log.info("TextProvider插件扩展: class={}, pluginId={}",
                        extension.getClass().getName(), extension.pluginId());
            }
        }
        if (!extensions.isEmpty()) {
            log.info("TextProvider自动模式命中插件实现: class={}, pluginId={}",
                    extensions.get(0).getClass().getName(), extensions.get(0).pluginId());
            return extensions.get(0).getText();
        }

        for (TextProvider provider : localProviders) {
            if ("default-text".equals(provider.pluginId())) {
                log.info("TextProvider自动模式回退本地实现: class={}, pluginId={}",
                        provider.getClass().getName(), provider.pluginId());
                return provider.getText();
            }
        }
        throw new RuntimeException("未找到默认文本提供者: default-text");
    }

    @Override
    public String getText(String pluginId) {
        String resolvedPluginId = StringUtils.hasText(pluginId) ? pluginId : "default-text";
        log.info("TextProvider指定模式: requestedPluginId={}", resolvedPluginId);

        // 优先使用插件扩展，便于覆盖默认实现
        List<TextProvider> extensions = pluginManager.getExtensions(TextProvider.class);
        for (TextProvider extension : extensions) {
            log.info("TextProvider指定模式遍历插件扩展: class={}, pluginId={}",
                    extension.getClass().getName(), extension.pluginId());
            if (resolvedPluginId.equals(extension.pluginId())) {
                log.info("TextProvider指定模式命中插件实现: class={}, pluginId={}",
                        extension.getClass().getName(), extension.pluginId());
                return extension.getText();
            }
        }

        // 回退到本地默认实现
        for (TextProvider provider : localProviders) {
            log.info("TextProvider指定模式遍历本地实现: class={}, pluginId={}",
                    provider.getClass().getName(), provider.pluginId());
            if (resolvedPluginId.equals(provider.pluginId())) {
                log.info("TextProvider指定模式命中本地实现: class={}, pluginId={}",
                        provider.getClass().getName(), provider.pluginId());
                return provider.getText();
            }
        }

        throw new RuntimeException("未找到文本提供者: " + resolvedPluginId);
    }
}
