package com.hoanghnt.swiftpay.event;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoanghnt.swiftpay.entity.OutboxEvent;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private static final String TOPIC = "swiftpay.users";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUserRegistered(UserRegisteredDomainEvent event) {
        // 1. Build the Kafka event from the newly created user
        User user = event.user();
        var kafkaEvent = new UserRegisteredEvent(
                UUID.randomUUID().toString(),
                "UserRegistered",
                Instant.now().toString(),
                user.getId().toString(),
                user.getUsername(),
                user.getEmail());
        // 2. Write to the outbox (BEFORE_COMMIT — same transaction as register)
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(user.getId().toString())
                .eventType("UserRegistered")
                .topic(TOPIC)
                .payload(toJson(kafkaEvent))
                .build());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize outbox payload for " + TOPIC, e);
        }
    }
}
