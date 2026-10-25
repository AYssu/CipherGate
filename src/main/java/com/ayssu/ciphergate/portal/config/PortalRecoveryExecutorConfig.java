package com.ayssu.ciphergate.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class PortalRecoveryExecutorConfig {
    @Bean(name = "portalRecoveryExecutor", destroyMethod = "shutdown")
    public ExecutorService portalRecoveryExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), task -> {
            Thread thread = new Thread(task, "portal-recovery-" + sequence.incrementAndGet());
            thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
