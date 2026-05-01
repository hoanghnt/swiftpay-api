package com.hoanghnt.swiftpay.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record TransferRequest(
    @NotBlank(message = "Receiver is required")
    String receiverUsername,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000", message = "Minimum transfer amount is 1,000 VND")
    @DecimalMax(value = "50000000", message = "Maximum transfer amount is 50,000,000 VND")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    BigDecimal amount,

    @Size(max = 255, message = "Description too long")
    String description
) {}
