package com.hoanghnt.swiftpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.reconciliation")
public class ReconciliationProperties {

    private boolean enabled = true;
    private long stuckAfterSeconds = 60;
    private long hardFailAfterSeconds = 900;
    private long fixedDelayMs = 30000;
}
