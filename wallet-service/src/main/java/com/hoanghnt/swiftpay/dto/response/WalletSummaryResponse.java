package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;

public record WalletSummaryResponse(
        long totalWallets,
        long frozenWallets,
        BigDecimal totalBalance) {
}
