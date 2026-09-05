package com.ciphergate.plugin.example;

import com.ciphergate.plugin.api.FunctionPlugin;
import org.pf4j.Extension;

import java.util.HashMap;
import java.util.Map;

/**
 * 示例函数插件 - 演示如何实现 FunctionPlugin 接口。
 * <p>
 * 提供两个示例函数：
 * 1. echo - 回显输入参数
 * 2. add - 两个数相加
 *
 * <p>客户端调用示例：
 * <pre>
 * {"type":"FUNC_CALL", "reqId":"123", "func":"echo", "params":{"message":"hello"}}
 * {"type":"FUNC_CALL", "reqId":"124", "func":"add", "params":{"a":1, "b":2}}
 * </pre>
 */
@Extension
public class ExampleFunctionPlugin implements FunctionPlugin {

    @Override
    public String pluginId() {
        return "example-function";
    }

    @Override
    public String functionName() {
        // 注意：一个插件类只能注册一个函数
        // 如果需要多个函数，需要创建多个类
        return "echo";
    }

    @Override
    public String description() {
        return "回显输入参数，返回相同的数据";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("echo", params);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
