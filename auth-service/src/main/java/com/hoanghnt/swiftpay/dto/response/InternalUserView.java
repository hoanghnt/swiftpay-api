package com.hoanghnt.swiftpay.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalUserView(
        UUID id,
        String username,
        String email,
        String phone,
        String fullName,
        String role,
        boolean emailVerified,
        boolean enabled,
        boolean locked,
        int failedLoginAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}
