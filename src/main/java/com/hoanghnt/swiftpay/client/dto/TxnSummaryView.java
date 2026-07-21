package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;

public record TxnSummaryView(
        long transactionsToday,
        BigDecimal transferVolumeToday) {
}
