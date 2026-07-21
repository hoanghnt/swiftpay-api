package com.hoanghnt.swiftpay.client.dto;

public record UserSummaryView(
        long totalUsers,
        long activeUsers,
        long lockedUsers) {
}
