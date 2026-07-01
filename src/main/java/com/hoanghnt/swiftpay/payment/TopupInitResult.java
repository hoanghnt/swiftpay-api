package com.hoanghnt.swiftpay.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupInitResult(
    UUID transactionId,
    String txnRef,
    String paymentUrl,
    BigDecimal amount,
    String status
) {}
