package com.ciphergate.plugin.example;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * 示例函数插件 PF4J 入口类。
 * <p>
 * 实际的函数实现在 Extension 类中（ExampleFunctionPlugin, AddFunctionPlugin）。
 */
public class ExampleFunctionPluginMain extends Plugin {

    public ExampleFunctionPluginMain(PluginWrapper wrapper) {
        super(wrapper);
    }
}
