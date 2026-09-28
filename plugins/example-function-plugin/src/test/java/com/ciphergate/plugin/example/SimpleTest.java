package com.ciphergate.plugin.example;

import com.ciphergate.plugin.api.FunctionPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 插件函数隔离测试，不依赖宿主服务。
 */
class SimpleTest {

    @Test
    void echoReturnsInput() throws Exception {
        FunctionPlugin echoPlugin = new ExampleFunctionPlugin();

        Map<String, Object> result = echoPlugin.execute(Map.of("message", "hello world"));

        assertNotNull(result.get("echo"));
        assertEquals(Map.of("message", "hello world"), result.get("echo"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    void addReturnsSum() throws Exception {
        FunctionPlugin addPlugin = new AddFunctionPlugin();

        Map<String, Object> result = addPlugin.execute(Map.of("a", 10, "b", 20));

        assertEquals(30.0, result.get("result"));
    }
}
