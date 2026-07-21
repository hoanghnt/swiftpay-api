package com.hoanghnt.swiftpay.event;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final WalletRepository walletRepository;

    @KafkaListener(topics = "swiftpay.users", groupId = "wallet-provisioning",
            containerFactory = "userRegisteredKafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        // 1. Filter for the right event type
        if (!"UserRegistered".equals(event.eventType())) {
            return;
        }
        try {
            // 2. Idempotency — skip if the wallet already exists
            UUID userId = UUID.fromString(event.userId());
            if (walletRepository.findByUserId(userId).isPresent()) {
                return;
            }
            // 3. Provision a wallet for the new user
            Wallet wallet = Wallet.builder()
                    .userId(userId)
                    .build();
            walletRepository.save(wallet);
            log.info("Wallet provisioned for userId={} via UserRegistered event", userId);
        } catch (DataIntegrityViolationException e) {
            log.debug("Wallet already provisioned (race with self-heal): eventId={}", event.eventId());
        } catch (Exception e) {
            log.warn("Failed to process UserRegistered event: eventId={}", event.eventId(), e);
        }
    }
}
