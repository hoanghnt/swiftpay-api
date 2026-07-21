package com.hoanghnt.swiftpay.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoanghnt.swiftpay.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    long countByPublishedAtIsNull();

    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.publishedAt IS NULL")
    LocalDateTime findOldestUnpublishedCreatedAt();

    @Modifying
    @Query(value = """
            DELETE FROM auth_outbox
            WHERE id IN (
                SELECT id FROM auth_outbox
                WHERE published_at IS NOT NULL AND published_at < :cutoff
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
