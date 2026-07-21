package com.hoanghnt.swiftpay.event;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.entity.OutboxEvent;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.UserRef;
import com.hoanghnt.swiftpay.repository.OutboxEventRepository;
import com.hoanghnt.swiftpay.repository.UserRefRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final OutboxEventRepository outboxRepository;
    private final UserRefRepository userRefRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTransactionCompleted(TransactionCompletedDomainEvent event) {
        // 1. Build the Kafka event from the transaction
        Transaction txn = event.transaction();
        var kafkaEvent = new TransactionCompletedEvent(
                UUID.randomUUID().toString(),
                "TransactionCompleted",
                Instant.now().toString(),
                txn.getId().toString(),
                txn.getSenderId().toString(),
                emailOf(txn.getSenderId()),
                txn.getReceiverId().toString(),
                emailOf(txn.getReceiverId()),
                txn.getAmount().toPlainString(),
                "VND");
        // 2. Write to the outbox (BEFORE_COMMIT — same transaction as the money movement)
        save("Transaction", txn.getId().toString(), "TransactionCompleted", "swiftpay.transactions", kafkaEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededDomainEvent event) {
        // 1. Build the Kafka event from the transaction
        Transaction txn = event.transaction();
        var kafkaEvent = new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                "PaymentSucceeded",
                Instant.now().toString(),
                txn.getId().toString(),
                "MOCK",
                txn.getVnpTxnRef(),
                txn.getReceiverId().toString(),
                emailOf(txn.getReceiverId()),
                txn.getAmount().toPlainString(),
                "VND");
        // 2. Write to the outbox (BEFORE_COMMIT — same transaction as the money movement)
        save("Payment", txn.getId().toString(), "PaymentSucceeded", "swiftpay.payments", kafkaEvent);
    }

    private String emailOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRefRepository.findById(userId).map(UserRef::getEmail).orElse(null);
    }

    private void save(String aggregateType, String aggregateId, String eventType, String topic, Object event) {
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .payload(toJson(topic, event))
                .build());
    }

    private String toJson(String topic, Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize outbox payload for " + topic, e);
        }
    }
}
