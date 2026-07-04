package com.hoanghnt.swiftpay.event;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.hoanghnt.swiftpay.entity.Transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCompleted(TransactionCompletedDomainEvent event) {
        Transaction txn = event.transaction();
        var kafkaEvent = new TransactionCompletedEvent(
                UUID.randomUUID().toString(),
                "TransactionCompleted",
                Instant.now().toString(),
                txn.getId().toString(),
                txn.getSender().getId().toString(),
                txn.getSender().getEmail(),
                txn.getReceiver().getId().toString(),
                txn.getReceiver().getEmail(),
                txn.getAmount().toPlainString(),
                "VND");
        publish("swiftpay.transactions", txn.getId().toString(), kafkaEvent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededDomainEvent event) {
        Transaction txn = event.transaction();
        var kafkaEvent = new PaymentSucceededEvent(
                UUID.randomUUID().toString(),
                "PaymentSucceeded",
                Instant.now().toString(),
                txn.getId().toString(),
                "MOCK",
                txn.getVnpTxnRef(),
                txn.getReceiver().getId().toString(),
                txn.getReceiver().getEmail(),
                txn.getAmount().toPlainString(),
                "VND");
        publish("swiftpay.payments", txn.getId().toString(), kafkaEvent);
    }

    private void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (Exception e) {
            log.warn("Failed to publish event to topic={}: {}", topic, e.getMessage());
        }
    }
}
