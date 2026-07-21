package com.hoanghnt.swiftpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.hoanghnt.swiftpay.config")
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
