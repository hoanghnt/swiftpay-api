package com.hoanghnt.swiftpay.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.hoanghnt.swiftpay.audit.AuditEvent;

public record AuditEventResponse(
    String id,
    String eventType,
    String actorUsername,
    UUID actorUserId,
    String targetUsername,
    UUID targetUserId,
    BigDecimal amount,
    String status,
    String failureReason,
    LocalDateTime occurredAt
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorUsername(),
                event.getActorUserId(),
                event.getTargetUsername(),
                event.getTargetUserId(),
                event.getAmount(),
                event.getStatus(),
                event.getFailureReason(),
                event.getOccurredAt());
    }
}
