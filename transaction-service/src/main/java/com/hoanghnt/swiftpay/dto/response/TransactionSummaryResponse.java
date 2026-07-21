package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
        long transactionsToday,
        BigDecimal transferVolumeToday) {
}
