package com.hoanghnt.swiftpay.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String username,
        String email,
        String message
) {
}