package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;

public record WalletLimitsResponse(
    BigDecimal minTransferAmount,
    BigDecimal maxTransferAmount,
    BigDecimal maxDailyTransfer,
    BigDecimal dailyTransferUsed,
    BigDecimal dailyTransferRemaining
) {}
