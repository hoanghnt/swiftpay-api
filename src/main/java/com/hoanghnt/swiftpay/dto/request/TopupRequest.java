package com.hoanghnt.swiftpay.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TopupRequest(
    @NotNull
    @DecimalMin(value = "10000", message = "Minimum topup is 10,000 VND")
    @DecimalMax(value = "50000000", message = "Maximum topup is 50,000,000 VND")
    BigDecimal amount
) {}