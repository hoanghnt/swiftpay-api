package com.hoanghnt.swiftpay.event;

public record PaymentSucceededEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String transactionId,
        String paymentProvider,
        String vnpTxnRef,
        String receiverUserId,
        String receiverEmail,
        String amount,
        String currency) {
}
