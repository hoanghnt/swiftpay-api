package com.hoanghnt.swiftpay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@Slf4j
public class EmailAsyncConfig {

    public static final String EMAIL_EXECUTOR = "emailExecutor";

    private static final int CORE_POOL = 2;
    private static final int MAX_POOL = 8;
    private static final int QUEUE_CAPACITY = 500;

    private final AtomicLong rejected = new AtomicLong();

    @Bean(name = EMAIL_EXECUTOR)
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL);
        executor.setMaxPoolSize(MAX_POOL);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            long n = rejected.incrementAndGet();
            if (n == 1 || n % 100 == 0) {
                log.warn("Hàng đợi email đầy (max={} queue={}) — đã bỏ {} email. "
                        + "Nghiệp vụ tiền KHÔNG bị ảnh hưởng.", MAX_POOL, QUEUE_CAPACITY, n);
            }
        });
        
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
