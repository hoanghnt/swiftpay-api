package com.hoanghnt.swiftpay.dto.response;

public record InternalUserSummary(
        long totalUsers,
        long activeUsers,
        long lockedUsers) {
}
