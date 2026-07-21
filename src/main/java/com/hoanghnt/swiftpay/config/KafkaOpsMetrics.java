package com.hoanghnt.swiftpay.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaOpsMetrics {

    private static final String LAG_METRIC = "swiftpay.kafka.consumer.lag";
    private static final String DLT_METRIC = "swiftpay.kafka.dlt.messages";

    private final KafkaAdmin kafkaAdmin;
    private final MeterRegistry meterRegistry;

    private final Map<String, AtomicLong> lagGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> dltGauges = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.kafka-ops.refresh-ms:30000}")
    public void refresh() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            measureConsumerLag(admin);
            measureDltDepth(admin);
        } catch (Exception e) {
            log.warn("Không thu thập được metric Kafka lần này: {}", e.toString());
        }
    }

    private void measureConsumerLag(AdminClient admin) throws Exception {
        for (ConsumerGroupListing group : admin.listConsumerGroups().valid().get()) {
            String groupId = group.groupId();
            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
            if (committed.isEmpty()) {
                continue;
            }

            Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
            committed.keySet().forEach(tp -> latestSpec.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                    admin.listOffsets(latestSpec).all().get();

            long totalLag = 0;
            for (var entry : committed.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                var end = ends.get(entry.getKey());
                if (end != null) {
                    totalLag += Math.max(0, end.offset() - entry.getValue().offset());
                }
            }
            gauge(lagGauges, LAG_METRIC, Tags.of("group", groupId), totalLag);
        }
    }

    private void measureDltDepth(AdminClient admin) throws Exception {
        Set<String> dltTopics = new HashSet<>();
        for (String topic : admin.listTopics().names().get()) {
            if (topic.endsWith(".DLT")) {
                dltTopics.add(topic);
            }
        }
        if (dltTopics.isEmpty()) {
            return;
        }

        var descriptions = admin.describeTopics(dltTopics).allTopicNames().get();
        Map<TopicPartition, OffsetSpec> earliest = new HashMap<>();
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        descriptions.forEach((topic, desc) -> desc.partitions().forEach(p -> {
            TopicPartition tp = new TopicPartition(topic, p.partition());
            earliest.put(tp, OffsetSpec.earliest());
            latest.put(tp, OffsetSpec.latest());
        }));

        var starts = admin.listOffsets(earliest).all().get();
        var ends = admin.listOffsets(latest).all().get();

        Map<String, Long> perTopic = new HashMap<>();
        ends.forEach((tp, end) -> {
            var start = starts.get(tp);
            long count = start == null ? 0 : Math.max(0, end.offset() - start.offset());
            perTopic.merge(tp.topic(), count, Long::sum);
        });

        perTopic.forEach((topic, count) -> {
            gauge(dltGauges, DLT_METRIC, Tags.of("topic", topic), count);
            if (count > 0) {
                log.warn("DLT '{}' đang giữ {} message — CÓ event bị bỏ, cần xem và replay.", topic, count);
            }
        });
    }

    private void gauge(Map<String, AtomicLong> holder, String name, Tags tags, long value) {
        holder.computeIfAbsent(name + tags, k -> meterRegistry.gauge(name, tags, new AtomicLong()))
                .set(value);
    }
}
