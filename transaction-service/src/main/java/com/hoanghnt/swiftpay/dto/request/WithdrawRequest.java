package com.hoanghnt.swiftpay.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WithdrawRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000", message = "Minimum withdraw is 10,000 VND")
    @DecimalMax(value = "50000000", message = "Maximum withdraw is 50,000,000 VND")
    BigDecimal amount,

    @NotBlank(message = "Bank account number is required")
    @Size(max = 20)
    String bankAccountNumber,

    @NotBlank(message = "Bank name is required")
    @Size(max = 50)
    String bankName
) {}
