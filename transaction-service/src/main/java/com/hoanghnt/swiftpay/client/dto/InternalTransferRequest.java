package com.hoanghnt.swiftpay.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalTransferRequest(
        UUID fromUserId,
        UUID toUserId,
        BigDecimal amount,
        String opKey
) {}
