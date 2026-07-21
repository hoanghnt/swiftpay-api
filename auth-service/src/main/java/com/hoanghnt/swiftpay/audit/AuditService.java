package com.hoanghnt.swiftpay.audit;

import java.time.LocalDateTime;
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
            String status, String failureReason) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .eventType(type.name())
                    .actorUsername(actorUsername)
                    .actorUserId(actorUserId)
                    .status(status)
                    .failureReason(failureReason)
                    .occurredAt(LocalDateTime.now())
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to save audit event: type={}, actor={}", type, actorUsername, e);
        }
    }

    public void logSuccess(AuditEventType type, String actorUsername, UUID actorUserId) {
        log(type, actorUsername, actorUserId, "SUCCESS", null);
    }

    public void logFailure(AuditEventType type, String actorUsername, UUID actorUserId, String reason) {
        log(type, actorUsername, actorUserId, "FAILURE", reason);
    }
}
