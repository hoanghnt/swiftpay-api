package com.hoanghnt.swiftpay.audit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public void log(AuditEventType type, String actorUsername, UUID actorUserId,
            String status, BigDecimal amount, String failureReason,
            Map<String, Object> metadata) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(type.name())
                    .actorUsername(actorUsername)
                    .actorUserId(actorUserId)
                    .amount(amount)
                    .status(status)
                    .failureReason(failureReason)
                    .metadata(metadata)
                    .occurredAt(LocalDateTime.now())
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to save audit event: type={}, actor={}", type, actorUsername, e);
        }
    }

    public void logTransfer(String senderUsername, UUID senderId,
            String receiverUsername, UUID receiverId, BigDecimal amount) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(AuditEventType.TRANSFER.name())
                    .actorUsername(senderUsername)
                    .actorUserId(senderId)
                    .targetUsername(receiverUsername)
                    .targetUserId(receiverId)
                    .amount(amount)
                    .status("SUCCESS")
                    .occurredAt(LocalDateTime.now())
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to save transfer audit event: sender={}, receiver={}", senderUsername, receiverUsername, e);
        }
    }
}
