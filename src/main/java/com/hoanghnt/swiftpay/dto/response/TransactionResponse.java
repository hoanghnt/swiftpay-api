package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID transactionId,
    String type,
    String status,
    BigDecimal amount,
    BigDecimal fee,
    String senderUsername,
    String receiverUsername,
    String description,
    LocalDateTime createdAt
) {}
