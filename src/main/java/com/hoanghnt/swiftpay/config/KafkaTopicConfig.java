package com.hoanghnt.swiftpay.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name("swiftpay.transactions")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name("swiftpay.payments")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
