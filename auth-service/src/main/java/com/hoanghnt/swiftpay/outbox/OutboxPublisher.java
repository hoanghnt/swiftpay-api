package com.hoanghnt.swiftpay.outbox;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hoanghnt.swiftpay.entity.OutboxEvent;
import com.hoanghnt.swiftpay.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository,
                           @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate) {
        this.repository = repository;
        this.outboxKafkaTemplate = outboxKafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:2000}")
    public void publishPending() {
        // 1. Fetch a batch of unpublished events (oldest first, preserving order)
        List<OutboxEvent> batch = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (batch.isEmpty()) {
            return;
        }

        // 2. Send to Kafka sequentially; stop on the first failure to keep ordering for the next poll
        List<OutboxEvent> published = new ArrayList<>();
        for (OutboxEvent e : batch) {
            try {
                outboxKafkaTemplate.send(e.getTopic(), e.getAggregateId(), e.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                e.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
                published.add(e);
            } catch (Exception ex) {
                log.warn("Outbox publish failed id={} topic={}: {}", e.getId(), e.getTopic(), ex.getMessage());
                break;
            }
        }

        // 3. Stamp published_at on the events that were sent successfully
        if (!published.isEmpty()) {
            repository.saveAll(published);
            log.debug("Outbox published {} event(s)", published.size());
        }
    }
}
