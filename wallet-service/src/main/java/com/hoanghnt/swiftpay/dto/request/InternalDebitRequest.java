package com.hoanghnt.swiftpay.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalDebitRequest(
        @NotNull UUID userId,
        @NotNull @DecimalMin(value = "0.0001", message = "Amount must be positive") BigDecimal amount,
        @NotBlank String opKey
) {}
