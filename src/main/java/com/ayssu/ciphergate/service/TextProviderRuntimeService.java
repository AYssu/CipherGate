package com.ayssu.ciphergate.service;

public interface TextProviderRuntimeService {

    /**
     * 自动选择实现（插件优先，本地默认兜底）。
     */
    String getText();

    /**
     * 根据插件ID获取文本（用于显式指定与调试）。
     */
    String getText(String pluginId);
}
