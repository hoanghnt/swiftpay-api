package com.hoanghnt.swiftpay.dto.response;

import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,       
    long expiresIn,          
    UUID userId,
    String username,
    String email,
    String role
) {}
