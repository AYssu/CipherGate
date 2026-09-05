package com.ciphergate.plugin.example;

import com.ciphergate.plugin.api.FunctionPlugin;

import java.util.*;

/**
 * 独立测试 - 直接在插件模块中运行
 */
public class SimpleTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====================================");
        System.out.println("  函数插件独立测试");
        System.out.println("====================================\n");

        // 测试 echo
        System.out.println("[测试1] Echo 函数");
        FunctionPlugin echoPlugin = new ExampleFunctionPlugin();
        Map<String, Object> params1 = Map.of("message", "hello world");
        Map<String, Object> result1 = echoPlugin.execute(params1);
        System.out.println("  输入: " + params1);
        System.out.println("  输出: " + result1);
        System.out.println("  ✓ Echo 函数测试通过\n");

        // 测试 add
        System.out.println("[测试2] Add 函数");
        FunctionPlugin addPlugin = new AddFunctionPlugin();
        Map<String, Object> params2 = Map.of("a", 10, "b", 20);
        Map<String, Object> result2 = addPlugin.execute(params2);
        System.out.println("  输入: " + params2);
        System.out.println("  输出: " + result2);
        double sum = (double) result2.get("result");
        assert sum == 30.0 : "期望 30.0，实际 " + sum;
        System.out.println("  ✓ Add 函数测试通过\n");

        System.out.println("====================================");
        System.out.println("  所有测试通过！");
        System.out.println("====================================");
    }
}
