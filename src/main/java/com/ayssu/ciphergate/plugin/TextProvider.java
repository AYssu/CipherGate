package com.ayssu.ciphergate.plugin;

import org.pf4j.ExtensionPoint;

/**
 * 文本输出扩展点，用于测试插件覆盖能力。
 */
public interface TextProvider extends ExtensionPoint {

    /**
     * 提供者标识。
     */
    String pluginId();

    /**
     * 获取文本内容。
     */
    String getText();
}
