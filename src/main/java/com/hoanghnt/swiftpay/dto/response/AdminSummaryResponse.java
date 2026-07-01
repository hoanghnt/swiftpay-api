package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;

public record AdminSummaryResponse(
    long totalUsers,
    long activeUsers,
    long lockedUsers,
    long totalWallets,
    long frozenWallets,
    BigDecimal totalBalanceInSystem,
    long transactionsToday,
    BigDecimal transferVolumeToday
) {}
