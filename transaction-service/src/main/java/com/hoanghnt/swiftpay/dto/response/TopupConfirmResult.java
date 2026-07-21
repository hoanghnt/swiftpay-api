package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupConfirmResult(
    UUID transactionId,
    String txnRef,
    BigDecimal amount,
    String status
) {}
