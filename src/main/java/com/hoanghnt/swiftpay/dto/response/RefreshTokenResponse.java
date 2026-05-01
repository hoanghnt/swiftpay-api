package com.hoanghnt.swiftpay.dto.response;

public record RefreshTokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}