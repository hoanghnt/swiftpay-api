package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;

public record WalletSummaryView(
        long totalWallets,
        long frozenWallets,
        BigDecimal totalBalance) {
}
