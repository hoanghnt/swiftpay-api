package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalDebitRequest(
        UUID userId,
        BigDecimal amount,
        String opKey
) {}
