package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AdminSummaryResponse(
    long totalUsers,
    long activeUsers,
    long lockedUsers,
    long totalWallets,
    long frozenWallets,
    BigDecimal totalBalanceInSystem,
    long transactionsToday,
    BigDecimal transferVolumeToday,
    boolean partial,
    List<String> unavailableServices
) {}
