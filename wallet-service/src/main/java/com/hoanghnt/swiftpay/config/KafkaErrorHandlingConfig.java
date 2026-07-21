package com.hoanghnt.swiftpay.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.ExponentialBackOff;

import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaErrorHandlingConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxAttempts(5);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                MessageConversionException.class,
                IllegalArgumentException.class);
        handler.setLogLevel(KafkaException.Level.ERROR);
        return handler;
    }

    @Bean
    public NewTopic usersDltTopic() {
        return TopicBuilder.name("swiftpay.users.DLT").partitions(1).replicas(1).build();
    }
}
