package com.hoanghnt.swiftpay.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
    List<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId);
    Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId, Pageable pageable);
    List<AuditEvent> findByEventTypeAndOccurredAtBetween(String eventType, LocalDateTime from, LocalDateTime to);
}
