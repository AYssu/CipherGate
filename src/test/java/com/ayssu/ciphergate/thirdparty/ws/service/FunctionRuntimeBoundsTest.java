package com.ayssu.ciphergate.thirdparty.ws.service;

import com.ayssu.ciphergate.service.FunctionPluginAppAccessService;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ciphergate.plugin.api.FunctionPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class FunctionRuntimeBoundsTest {
    private FunctionRuntimeService service;
    private FunctionPlugin plugin;
    private FunctionPluginAppAccessService access;
    private ThreadPoolExecutor executor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        access = mock(FunctionPluginAppAccessService.class);
        service = new FunctionRuntimeService(mock(PluginManager.class), access);
        ReflectionTestUtils.setField(service, "timeoutMs", 1000L);
        executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(service, "executor");
        plugin = mock(FunctionPlugin.class);
        Map<String, ConcurrentHashMap<String, FunctionPlugin>> registry =
                (Map<String, ConcurrentHashMap<String, FunctionPlugin>>) ReflectionTestUtils.getField(service, "registry");
        registry.put("test", new ConcurrentHashMap<>(Map.of("work", plugin)));
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void normalExecutionStillRequiresApplicationAllowlist() throws Exception {
        when(plugin.execute(anyMap())).thenReturn(Map.of("value", 42));
        assertEquals("PLUGIN_ACCESS_DENIED", service.executeFunction(1L, "test", "work", Map.of()).code());
        verifyNoInteractions(plugin);
        when(access.isAllowed("test", 1L)).thenReturn(true);
        assertEquals(Map.of("value", 42), service.executeFunction(1L, "test", "work", Map.of()).data());
    }

    @Test
    void timeoutInterruptsTheRunningPlugin() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        when(plugin.execute(anyMap())).thenAnswer(invocation -> {
            try {
                new CountDownLatch(1).await();
                return Map.of();
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
            }
        });
        assertEquals("TIMEOUT", service.executeFunctionForAdmin("test", "work", Map.of()).code());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test
    void saturatedExecutorRejectsAndQueuedTimeoutReleasesCapacity() throws Exception {
        CountDownLatch running = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            executor.submit(() -> {
                running.countDown();
                release.await();
                return null;
            });
        }
        assertTrue(running.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 64; i++) {
            executor.submit(() -> {});
        }
        assertEquals(8, executor.getPoolSize());
        assertEquals(64, executor.getQueue().size());
        assertEquals("SERVER_BUSY", service.executeFunctionForAdmin("test", "work", Map.of()).code());
        executor.getQueue().poll();
        assertEquals("TIMEOUT", service.executeFunctionForAdmin("test", "work", Map.of()).code());
        assertEquals(63, executor.getQueue().size(), "Cancelled queued work must release its slot");
        verifyNoInteractions(plugin);
        release.countDown();
    }

    @Test
    void interruptedCallerCancelsWorkAndPreservesInterruptFlag() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(plugin.execute(anyMap())).thenAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return Map.of();
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
            }
        });
        AtomicBoolean restored = new AtomicBoolean();
        CompletableFuture<FunctionResult> outcome = new CompletableFuture<>();
        Thread caller = new Thread(() -> {
            FunctionResult result = service.executeFunctionForAdmin("test", "work", Map.of());
            restored.set(Thread.currentThread().isInterrupted());
            outcome.complete(result);
        });
        ReflectionTestUtils.setField(service, "timeoutMs", 30000L);
        caller.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        caller.interrupt();
        assertEquals("INTERRUPTED", outcome.get(5, TimeUnit.SECONDS).code());
        assertTrue(restored.get());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        caller.join(5000);
        assertFalse(caller.isAlive());
    }

    @Test
    void shutdownRejectsFurtherWork() {
        service.shutdown();
        assertEquals("SERVER_BUSY", service.executeFunctionForAdmin("test", "work", Map.of()).code());
    }
}
