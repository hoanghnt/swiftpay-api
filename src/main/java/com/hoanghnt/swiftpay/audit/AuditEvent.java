package com.hoanghnt.swiftpay.audit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Document(collection = "audit_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    private String id;

    private String eventType;
    private String actorUsername;
    private UUID actorUserId;
    private String targetUsername;
    private UUID targetUserId;
    private BigDecimal amount;
    private String status;
    private String failureReason;
    private Map<String, Object> metadata;
    private LocalDateTime occurredAt;
    private String ipAddress;
}
