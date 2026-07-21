package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalCreditRequest(
        UUID userId,
        BigDecimal amount,
        String opKey
) {}
