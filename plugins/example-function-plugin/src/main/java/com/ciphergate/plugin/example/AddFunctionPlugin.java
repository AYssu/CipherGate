package com.ciphergate.plugin.example;

import com.ciphergate.plugin.api.FunctionPlugin;
import org.pf4j.Extension;

import java.util.HashMap;
import java.util.Map;

/**
 * 示例函数插件 - 加法运算。
 *
 * <p>客户端调用示例：
 * <pre>
 * {"type":"FUNC_CALL", "reqId":"124", "func":"add", "params":{"a":1, "b":2}}
 * </pre>
 */
@Extension
public class AddFunctionPlugin implements FunctionPlugin {

    @Override
    public String pluginId() {
        return "example-function";
    }

    @Override
    public String functionName() {
        return "add";
    }

    @Override
    public String description() {
        return "两个数相加，返回结果";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) throws Exception {
        Number a = (Number) params.getOrDefault("a", 0);
        Number b = (Number) params.getOrDefault("b", 0);

        Map<String, Object> result = new HashMap<>();
        result.put("result", a.doubleValue() + b.doubleValue());
        result.put("a", a);
        result.put("b", b);
        return result;
    }
}
