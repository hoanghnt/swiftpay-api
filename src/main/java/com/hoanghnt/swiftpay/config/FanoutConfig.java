package com.hoanghnt.swiftpay.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FanoutConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService adminFanoutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
