package com.ayssu.ciphergate;

import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 函数插件快速测试。
 * 直接运行，不需要登录。
 */
@SpringBootTest
public class FunctionPluginTest {

    @Autowired
    private FunctionRuntimeService functionRuntimeService;

    @Test
    public void testListFunctions() {
        // 查看已注册的函数
        Map<String, List<String>> functions = functionRuntimeService.listFunctions();
        System.out.println("=== 已注册的函数 ===");
        functions.forEach((pluginId, funcList) -> {
            System.out.println("插件: " + pluginId);
            funcList.forEach(f -> System.out.println("  - " + f));
        });
    }

    @Test
    public void testEchoFunction() {
        // 测试 echo 函数
        Map<String, Object> params = Map.of("message", "hello world");
        FunctionResult result = functionRuntimeService.executeFunction(
                "example-function", "echo", params);

        System.out.println("=== Echo 函数测试 ===");
        System.out.println("成功: " + result.success());
        System.out.println("数据: " + result.data());

        assertTrue(result.success());
        assertNotNull(result.data());
    }

    @Test
    public void testAddFunction() {
        // 测试 add 函数
        Map<String, Object> params = Map.of("a", 10, "b", 20);
        FunctionResult result = functionRuntimeService.executeFunction(
                "example-function", "add", params);

        System.out.println("=== Add 函数测试 ===");
        System.out.println("成功: " + result.success());
        System.out.println("数据: " + result.data());

        assertTrue(result.success());
        assertEquals(30.0, result.data().get("result"));
    }

    @Test
    public void testFunctionNotFound() {
        // 测试函数不存在
        FunctionResult result = functionRuntimeService.executeFunction(
                "example-function", "nonexistent", Map.of());

        System.out.println("=== 函数不存在测试 ===");
        System.out.println("成功: " + result.success());
        System.out.println("错误码: " + result.code());

        assertFalse(result.success());
        assertEquals("FUNC_NOT_FOUND", result.code());
    }
}
