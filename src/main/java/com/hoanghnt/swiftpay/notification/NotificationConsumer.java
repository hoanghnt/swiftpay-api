package com.hoanghnt.swiftpay.notification;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.hoanghnt.swiftpay.event.PaymentSucceededEvent;
import com.hoanghnt.swiftpay.event.TransactionCompletedEvent;
import com.hoanghnt.swiftpay.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "swiftpay.transactions", groupId = "notification-service",
            containerFactory = "transactionCompletedKafkaListenerContainerFactory")
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        if (!"TransactionCompleted".equals(event.eventType())) {
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(event.amount());
            UUID transactionId = UUID.fromString(event.transactionId());
            emailService.sendTransferSentEmail(
                    event.senderEmail(), null, null, amount, transactionId);
            emailService.sendTransferReceivedEmail(
                    event.receiverEmail(), null, null, amount, transactionId);
        } catch (Exception e) {
            log.warn("Failed to process TransactionCompleted event: eventId={}", event.eventId(), e);
        }
    }

    @KafkaListener(topics = "swiftpay.payments", groupId = "notification-service",
            containerFactory = "paymentSucceededKafkaListenerContainerFactory")
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (!"PaymentSucceeded".equals(event.eventType())) {
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(event.amount());
            UUID transactionId = UUID.fromString(event.transactionId());
            emailService.sendTopupSuccessEmail(event.receiverEmail(), null, amount, transactionId);
        } catch (Exception e) {
            log.warn("Failed to process PaymentSucceeded event: eventId={}", event.eventId(), e);
        }
    }
}
