package com.hoanghnt.swiftpay.event;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.entity.UserRef;
import com.hoanghnt.swiftpay.repository.UserRefRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRefConsumer {

    private final UserRefRepository userRefRepository;

    @KafkaListener(topics = "swiftpay.users", groupId = "txn-user-ref",
            containerFactory = "userRegisteredKafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (!"UserRegistered".equals(event.eventType())) {
            return;
        }
        try {
            UUID userId = UUID.fromString(event.userId());
            UserRef ref = userRefRepository.findById(userId)
                    .orElseGet(() -> UserRef.builder().id(userId).build());
            ref.setUsername(event.username());
            ref.setEmail(event.email());
            userRefRepository.save(ref);
            log.info("user_ref upserted: userId={} username={}", userId, event.username());
        } catch (DataIntegrityViolationException e) {
            log.debug("user_ref race: eventId={}", event.eventId());
        } catch (Exception e) {
            log.warn("Failed to process UserRegistered for user_ref: eventId={}", event.eventId(), e);
        }
    }
}
