package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserDetailResponse(
    UUID id,
    String username,
    String email,
    String phone,
    String fullName,
    String role,
    boolean emailVerified,
    boolean enabled,
    int failedLoginAttempts,
    LocalDateTime lockedUntil,
    LocalDateTime lastLoginAt,
    UUID walletId,
    BigDecimal balance,
    String currency,
    boolean walletFrozen,
    boolean partial,
    java.util.List<String> unavailableServices
) {}
