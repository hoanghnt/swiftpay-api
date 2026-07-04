package com.hoanghnt.swiftpay.event;

public record TransactionCompletedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String transactionId,
        String senderUserId,
        String senderEmail,
        String receiverUserId,
        String receiverEmail,
        String amount,
        String currency) {
}
