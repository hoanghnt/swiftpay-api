package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupResponse(
    UUID transactionId,
    String txnRef,
    String paymentUrl,
    BigDecimal amount,
    String status
) {}
