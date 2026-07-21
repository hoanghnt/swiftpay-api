package com.hoanghnt.swiftpay.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.wallet")
public class WalletProperties {

    private BigDecimal maxDailyTransfer;
    private BigDecimal minTransferAmount;
    private BigDecimal maxTransferAmount;
    private BigDecimal withdrawFeePercent;
}
