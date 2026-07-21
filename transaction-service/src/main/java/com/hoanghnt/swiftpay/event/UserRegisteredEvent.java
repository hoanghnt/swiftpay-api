package com.hoanghnt.swiftpay.event;

public record UserRegisteredEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String userId,
        String username,
        String email) {
}
