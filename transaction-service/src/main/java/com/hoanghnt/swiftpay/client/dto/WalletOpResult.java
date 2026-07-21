package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletOpResult(
        String opKey,
        String opType,
        BigDecimal amount,
        boolean applied,
        boolean idempotentReplay,
        LocalDateTime appliedAt
) {}
