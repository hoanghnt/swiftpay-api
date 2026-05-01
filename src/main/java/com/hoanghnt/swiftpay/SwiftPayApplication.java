package com.hoanghnt.swiftpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan("com.hoanghnt.swiftpay.config")
public class SwiftPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwiftPayApplication.class, args);
    }
}
