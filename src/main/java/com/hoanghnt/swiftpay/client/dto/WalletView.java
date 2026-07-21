package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletView(
        UUID id,
        BigDecimal balance,
        String currency,
        boolean frozen,
        LocalDateTime createdAt) {
}
