package com.hoanghnt.swiftpay.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserView(
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
