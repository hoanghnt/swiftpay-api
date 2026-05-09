package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WithdrawResponse(
    UUID transactionId,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal netAmount,    // amount - fee
    String status,
    String bankAccountNumber,
    String bankName,
    LocalDateTime createdAt
) {}