package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletOperationResponse(
        String opKey,
        String opType,
        BigDecimal amount,
        boolean applied,
        boolean idempotentReplay,
        LocalDateTime appliedAt
) {}
