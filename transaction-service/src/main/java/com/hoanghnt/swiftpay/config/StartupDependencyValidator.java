package com.hoanghnt.swiftpay.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDependencyValidator implements ApplicationRunner {

    private static final int PROBE_TIMEOUT_MS = 10_000;

    private final ObjectProvider<KafkaAdmin> kafkaAdmin;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    @Value("${app.startup.fail-fast:true}")
    private boolean failFast;

    @Override
    public void run(ApplicationArguments args) {
        List<String> failures = new ArrayList<>();

        checkKafka(failures);
        checkRedis(failures);

        if (failures.isEmpty()) {
            log.info("Startup check: mọi dependency bắt buộc đều kết nối được.");
            return;
        }

        String detail = String.join(" | ", failures);
        if (!failFast) {
            log.error("Startup check THẤT BẠI nhưng app.startup.fail-fast=false nên vẫn chạy tiếp: {}", detail);
            return;
        }
        throw new IllegalStateException(
                "Không kết nối được dependency bắt buộc — dừng khởi động thay vì chạy trong trạng thái hỏng: "
                        + detail);
    }

    private void checkKafka(List<String> failures) {
        KafkaAdmin admin = kafkaAdmin.getIfAvailable();
        if (admin == null) {
            return; 
        }
        Map<String, Object> props = admin.getConfigurationProperties();
        Object servers = props.get("bootstrap.servers");
        try (AdminClient client = AdminClient.create(props)) {
            var cluster = client.describeCluster(new DescribeClusterOptions().timeoutMs(PROBE_TIMEOUT_MS));
            int brokers = cluster.nodes().get().size();
            log.info("Startup check: Kafka OK — bootstrap={} brokers={}", servers, brokers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add("Kafka probe bị ngắt (bootstrap.servers=" + servers + ")");
        } catch (Exception e) {
            failures.add("Kafka không kết nối được (bootstrap.servers=" + servers + "): " + rootMessage(e));
        }
    }

    private void checkRedis(List<String> failures) {
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return; 
        }
        try (RedisConnection conn = factory.getConnection()) {
            conn.ping();
            log.info("Startup check: Redis OK");
        } catch (Exception e) {
            failures.add("Redis không kết nối được: " + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}
