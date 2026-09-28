package com.ayssu.ciphergate;

import com.ayssu.ciphergate.service.FunctionPluginAppAccessService;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ciphergate.plugin.api.FunctionPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.DefaultPluginManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 函数插件运行时隔离测试。
 * 不依赖数据库、Spring 上下文或磁盘上的真实插件包。
 */
public class FunctionPluginTest {

    private FunctionRuntimeService functionRuntimeService;
    private MutableAccessService accessService;

    @BeforeEach
    public void setUp() throws Exception {
        accessService = new MutableAccessService();
        Path pluginRoot = Files.createTempDirectory("ciphergate-function-plugin-test");
        functionRuntimeService = new FunctionRuntimeService(
                new DefaultPluginManager(pluginRoot), accessService);
        ReflectionTestUtils.setField(functionRuntimeService, "timeoutMs", 1000L);

        @SuppressWarnings("unchecked")
        Map<String, ConcurrentHashMap<String, FunctionPlugin>> registry =
                (Map<String, ConcurrentHashMap<String, FunctionPlugin>>) ReflectionTestUtils.getField(
                        functionRuntimeService, "registry");
        ConcurrentHashMap<String, FunctionPlugin> functions = new ConcurrentHashMap<>();
        functions.put("echo", new TestFunctionPlugin("echo") {
            @Override
            public Map<String, Object> execute(Map<String, Object> params) {
                return Map.of("echo", params.get("message"), "timestamp", 1L);
            }
        });
        functions.put("add", new TestFunctionPlugin("add") {
            @Override
            public Map<String, Object> execute(Map<String, Object> params) {
                double value = ((Number) params.get("a")).doubleValue()
                        + ((Number) params.get("b")).doubleValue();
                return Map.of("result", value);
            }
        });
        registry.put("example-function", functions);
    }

    @Test
    public void testListFunctions() {
        Map<String, List<String>> functions = functionRuntimeService.listFunctions();

        assertEquals(java.util.Set.of("echo", "add"), new java.util.HashSet<>(functions.get("example-function")));
    }

    @Test
    public void testEchoFunction() {
        FunctionResult result = functionRuntimeService.executeFunctionForAdmin(
                "example-function", "echo", Map.of("message", "hello world"));

        assertTrue(result.success());
        assertEquals("hello world", result.data().get("echo"));
    }

    @Test
    public void testAddFunction() {
        FunctionResult result = functionRuntimeService.executeFunctionForAdmin(
                "example-function", "add", Map.of("a", 10, "b", 20));

        assertTrue(result.success());
        assertEquals(30.0, result.data().get("result"));
    }

    @Test
    public void testFunctionNotFound() {
        FunctionResult result = functionRuntimeService.executeFunctionForAdmin(
                "example-function", "nonexistent", Map.of());

        assertFalse(result.success());
        assertEquals("FUNC_NOT_FOUND", result.code());
    }

    @Test
    public void testApplicationAccessIsDeniedByDefault() {
        FunctionResult denied = functionRuntimeService.executeFunction(
                100L, "example-function", "echo", Map.of("message", "denied"));

        assertFalse(denied.success());
        assertEquals("PLUGIN_ACCESS_DENIED", denied.code());

        accessService.allow("example-function", 100L);
        FunctionResult allowed = functionRuntimeService.executeFunction(
                100L, "example-function", "echo", Map.of("message", "allowed"));

        assertTrue(allowed.success());
        assertEquals("allowed", allowed.data().get("echo"));
    }

    @Test
    public void testApplicationAccessRequiresPluginIdAndAppId() {
        FunctionResult missingPlugin = functionRuntimeService.executeFunction(
                100L, " ", "echo", Map.of());
        FunctionResult missingApp = functionRuntimeService.executeFunction(
                null, "example-function", "echo", Map.of());

        assertEquals("PLUGIN_ID_REQUIRED", missingPlugin.code());
        assertEquals("APP_REQUIRED", missingApp.code());
    }

    private abstract static class TestFunctionPlugin implements FunctionPlugin {
        private final String functionName;

        private TestFunctionPlugin(String functionName) {
            this.functionName = functionName;
        }

        @Override
        public String pluginId() {
            return "example-function";
        }

        @Override
        public String functionName() {
            return functionName;
        }
    }

    private static final class MutableAccessService implements FunctionPluginAppAccessService {
        private final Map<String, List<Long>> allowed = new LinkedHashMap<>();

        private void allow(String pluginId, Long appId) {
            allowed.computeIfAbsent(pluginId, key -> new ArrayList<>()).add(appId);
        }

        @Override
        public boolean isAllowed(String pluginId, Long appId) {
            return appId != null && allowed.getOrDefault(pluginId, List.of()).contains(appId);
        }

        @Override
        public List<Long> getAllowedAppIds(String pluginId) {
            return List.copyOf(allowed.getOrDefault(pluginId, List.of()));
        }

        @Override
        public void replaceAllowedAppIds(String pluginId, List<Long> appIds) {
            allowed.put(pluginId, List.copyOf(appIds));
        }
    }
}
