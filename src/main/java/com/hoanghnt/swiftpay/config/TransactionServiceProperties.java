package com.hoanghnt.swiftpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.transaction-service")
public class TransactionServiceProperties {

    private String baseUrl;
    private String internalApiKey;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
}
