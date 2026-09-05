package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ciphergate.plugin.api.FunctionPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

/**
 * 插件函数执行运行时服务。
 * <p>
 * 职责：
 * 1. 启动时扫描所有已加载的 PF4J 插件，发现 FunctionPlugin 实现
 * 2. 维护 pluginId -> functionName -> FunctionPlugin 的注册表
 * 3. 提供带超时控制的函数执行能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionRuntimeService {

    @Value("${app.ws.func-timeout-ms:30000}")
    private long timeoutMs;

    private final PluginManager pluginManager;

    /** pluginId -> (functionName -> FunctionPlugin) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, FunctionPlugin>> registry = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "func-exec");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        discoverPlugins();
    }

    /**
     * 扫描所有已加载的 PF4J 插件，发现 FunctionPlugin 实现。
     */
    public void discoverPlugins() {
        registry.clear();
        int count = 0;

        for (PluginWrapper pw : pluginManager.getPlugins()) {
            if (pw.getPlugin() == null) continue;
            ClassLoader cl = pw.getPluginClassLoader();
            String pluginId = pw.getPluginId();

            List<FunctionPlugin> plugins = discoverFromPlugin(pluginId, cl);
            if (!plugins.isEmpty()) {
                ConcurrentHashMap<String, FunctionPlugin> funcMap = new ConcurrentHashMap<>();
                for (FunctionPlugin fp : plugins) {
                    funcMap.put(fp.functionName(), fp);
                    log.info("注册函数插件: pluginId={}, function={}, desc={}",
                            pluginId, fp.functionName(), fp.description());
                }
                registry.put(pluginId, funcMap);
                count += plugins.size();
            }
        }

        log.info("函数插件扫描完成: totalFunctions={}, pluginCount={}", count, registry.size());
    }

    /**
     * 从单个插件中发现 FunctionPlugin 实现。
     */
    private List<FunctionPlugin> discoverFromPlugin(String pluginId, ClassLoader cl) {
        List<FunctionPlugin> result = new ArrayList<>();

        // 方式1: 读 extensions.idx
        if (scanExtensionsIndex(cl, result)) {
            return result;
        }

        // 方式2: 扫描 JAR 中带 @Extension 注解的类
        scanPluginJar(cl, result);
        return result;
    }

    private boolean scanExtensionsIndex(ClassLoader cl, List<FunctionPlugin> result) {
        try {
            java.io.InputStream is = cl.getResourceAsStream("META-INF/extensions.idx");
            if (is == null) return false;
            String index = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String className : index.split("\n")) {
                className = className.trim();
                if (className.isEmpty()) continue;
                try {
                    Class<?> clazz = cl.loadClass(className);
                    if (FunctionPlugin.class.isAssignableFrom(clazz)) {
                        FunctionPlugin instance = (FunctionPlugin) clazz.getDeclaredConstructor().newInstance();
                        result.add(instance);
                    }
                } catch (Exception e) {
                    log.debug("加载扩展类失败: {}", className, e);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void scanPluginJar(ClassLoader cl, List<FunctionPlugin> result) {
        try {
            if (cl instanceof java.net.URLClassLoader urlCl) {
                for (java.net.URL url : urlCl.getURLs()) {
                    if (!url.toString().endsWith(".jar")) continue;
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new java.io.File(url.toURI()))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.endsWith(".class") || name.contains("$")) continue;
                            String className = name.replace('/', '.').replace(".class", "");
                            try {
                                Class<?> clazz = cl.loadClass(className);
                                if (clazz.isAnnotationPresent(org.pf4j.Extension.class)
                                        && FunctionPlugin.class.isAssignableFrom(clazz)) {
                                    FunctionPlugin instance = (FunctionPlugin) clazz.getDeclaredConstructor().newInstance();
                                    result.add(instance);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("扫描 JAR 失败", e);
        }
    }

    /**
     * 执行函数（带超时控制）。
     *
     * @param pluginId   插件ID
     * @param functionName 函数名称
     * @param params     输入参数
     * @return 执行结果
     */
    public FunctionResult executeFunction(String pluginId, String functionName, Map<String, Object> params) {
        // 查找插件
        ConcurrentHashMap<String, FunctionPlugin> funcMap = registry.get(pluginId);
        if (funcMap == null) {
            return FunctionResult.error("PLUGIN_NOT_FOUND", "插件不存在: " + pluginId);
        }

        FunctionPlugin plugin = funcMap.get(functionName);
        if (plugin == null) {
            return FunctionResult.error("FUNC_NOT_FOUND", "函数不存在: " + functionName);
        }

        // 带超时执行
        try {
            Future<Map<String, Object>> future = executor.submit(() -> plugin.execute(params));
            Map<String, Object> result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return FunctionResult.ok(result);
        } catch (TimeoutException e) {
            return FunctionResult.error("TIMEOUT", "函数执行超时(" + timeoutMs + "ms): " + functionName);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            log.error("函数执行异常: pluginId={}, function={}, error={}", pluginId, functionName, msg, e);
            return FunctionResult.error("EXEC_ERROR", "函数执行异常: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FunctionResult.error("INTERRUPTED", "函数执行被中断");
        } catch (Exception e) {
            return FunctionResult.error("EXEC_ERROR", "函数执行失败: " + e.getMessage());
        }
    }

    /**
     * 检查指定插件和函数是否存在。
     */
    public boolean functionExists(String pluginId, String functionName) {
        ConcurrentHashMap<String, FunctionPlugin> funcMap = registry.get(pluginId);
        return funcMap != null && funcMap.containsKey(functionName);
    }

    /**
     * 根据函数名查找对应的插件ID。
     */
    public String findPluginByFunction(String functionName) {
        for (var entry : registry.entrySet()) {
            if (entry.getValue().containsKey(functionName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 获取所有已注册的函数列表。
     */
    public Map<String, List<String>> listFunctions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : registry.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().keySet()));
        }
        return result;
    }

    /**
     * 重新扫描插件（插件热加载后调用）。
     */
    public void refresh() {
        log.info("刷新函数插件注册表...");
        discoverPlugins();
    }
}
