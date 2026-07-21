package com.hoanghnt.swiftpay.outbox;

import com.hoanghnt.swiftpay.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class OutboxMetrics {

    private final OutboxEventRepository repository;
    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        this.repository = repository;
        registry.gauge("swiftpay.outbox.unpublished", unpublished);
        registry.gauge("swiftpay.outbox.oldest.age.seconds", oldestAgeSeconds);
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-refresh-ms:30000}")
    public void refresh() {
        try {
            unpublished.set(repository.countByPublishedAtIsNull());
            LocalDateTime oldest = repository.findOldestUnpublishedCreatedAt();
            oldestAgeSeconds.set(oldest == null ? 0 : Duration.between(oldest, LocalDateTime.now()).toSeconds());
        } catch (Exception e) {
            log.warn("Không đo được outbox lần này: {}", e.toString());
        }
    }
}
