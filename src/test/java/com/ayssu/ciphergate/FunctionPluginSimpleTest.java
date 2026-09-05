package com.ayssu.ciphergate;

import com.ciphergate.plugin.api.FunctionPlugin;

import java.util.*;

/**
 * 独立测试 - 不需要启动服务，不需要登录。
 * 直接运行 main 方法即可测试。
 */
public class FunctionPluginSimpleTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====================================");
        System.out.println("  函数插件独立测试");
        System.out.println("====================================\n");

        // 1. 测试插件类加载
        testPluginLoading();

        // 2. 测试 echo 函数
        testEchoFunction();

        // 3. 测试 add 函数
        testAddFunction();

        System.out.println("\n====================================");
        System.out.println("  所有测试通过！");
        System.out.println("====================================");
    }

    static void testPluginLoading() {
        System.out.println("[测试1] 插件类加载");
        try {
            // 直接实例化插件类
            Class<?> echoClass = Class.forName("com.ciphergate.plugin.example.ExampleFunctionPlugin");
            FunctionPlugin echoPlugin = (FunctionPlugin) echoClass.getDeclaredConstructor().newInstance();

            Class<?> addClass = Class.forName("com.ciphergate.plugin.example.AddFunctionPlugin");
            FunctionPlugin addPlugin = (FunctionPlugin) addClass.getDeclaredConstructor().newInstance();

            System.out.println("  ✓ Echo 插件加载成功: " + echoPlugin.functionName());
            System.out.println("  ✓ Add 插件加载成功: " + addPlugin.functionName());
        } catch (Exception e) {
            System.out.println("  ✗ 插件加载失败: " + e.getMessage());
            throw new RuntimeException(e);
        }
        System.out.println();
    }

    static void testEchoFunction() throws Exception {
        System.out.println("[测试2] Echo 函数");
        FunctionPlugin echoPlugin = (FunctionPlugin) Class.forName(
                "com.ciphergate.plugin.example.ExampleFunctionPlugin")
                .getDeclaredConstructor().newInstance();

        Map<String, Object> params = new HashMap<>();
        params.put("message", "hello world");
        params.put("number", 123);

        Map<String, Object> result = echoPlugin.execute(params);

        System.out.println("  输入: " + params);
        System.out.println("  输出: " + result);

        // 验证
        assert result.containsKey("echo") : "结果应包含 echo 字段";
        assert result.containsKey("timestamp") : "结果应包含 timestamp 字段";
        System.out.println("  ✓ Echo 函数测试通过\n");
    }

    static void testAddFunction() throws Exception {
        System.out.println("[测试3] Add 函数");
        FunctionPlugin addPlugin = (FunctionPlugin) Class.forName(
                "com.ciphergate.plugin.example.AddFunctionPlugin")
                .getDeclaredConstructor().newInstance();

        Map<String, Object> params = new HashMap<>();
        params.put("a", 10);
        params.put("b", 20);

        Map<String, Object> result = addPlugin.execute(params);

        System.out.println("  输入: " + params);
        System.out.println("  输出: " + result);

        // 验证
        double expectedResult = (double) result.get("result");
        assert expectedResult == 30.0 : "10 + 20 应该等于 30，实际是 " + expectedResult;
        System.out.println("  ✓ Add 函数测试通过\n");
    }
}
