package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletResponse(
    UUID walletId,
    BigDecimal balance,
    String currency,
    boolean frozen,
    LocalDateTime createdAt
) {}
