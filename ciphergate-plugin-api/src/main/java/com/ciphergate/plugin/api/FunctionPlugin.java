package com.ciphergate.plugin.api;

import org.pf4j.ExtensionPoint;

import java.util.Map;

/**
 * 插件函数执行扩展点。
 * <p>
 * 实现此接口的类用 {@code @Extension} 注解标记，由 PF4J 自动发现。
 * 客户端通过 WebSocket 发送 FUNC_CALL 消息，指定函数名称和参数，
 * 服务端调用对应插件的 execute 方法并返回结果。
 *
 * <pre>
 * 客户端请求:
 * {"type":"FUNC_CALL", "reqId":"uuid", "func":"getUserInfo", "params":{"userId":123}}
 *
 * 服务端响应:
 * {"type":"FUNC_RESULT", "reqId":"uuid", "func":"getUserInfo", "data":{"name":"test"}}
 * </pre>
 */
public interface FunctionPlugin extends ExtensionPoint {

    /**
     * 插件唯一标识，与 plugin.properties 中 plugin.id 一致。
     * 同一个插件可以注册多个函数。
     */
    String pluginId();

    /**
     * 函数名称，客户端通过此名称调用。
     * 在同一个 pluginId 下必须唯一。
     */
    String functionName();

    /**
     * 函数描述（可选，用于文档/调试）。
     */
    default String description() {
        return "";
    }

    /**
     * 执行函数。
     *
     * @param params 客户端传入的参数 (JSON Key-Value)
     * @return 执行结果，将作为 FUNC_RESULT 的 data 字段返回
     * @throws Exception 执行过程中的异常，将被包装为 FUNC_ERROR
     */
    Map<String, Object> execute(Map<String, Object> params) throws Exception;
}
