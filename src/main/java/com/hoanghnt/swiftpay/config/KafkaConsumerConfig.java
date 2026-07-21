package com.hoanghnt.swiftpay.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.hoanghnt.swiftpay.event.PaymentSucceededEvent;
import com.hoanghnt.swiftpay.event.TransactionCompletedEvent;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-service}")
    private String groupId;

    private Map<String, Object> baseConsumerProps() {
        return baseConsumerProps(groupId);
    }

    private Map<String, Object> baseConsumerProps(String groupId) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    }

    @Bean
    public ConsumerFactory<String, TransactionCompletedEvent> transactionCompletedConsumerFactory() {
        JsonDeserializer<TransactionCompletedEvent> valueDeserializer =
                new JsonDeserializer<>(TransactionCompletedEvent.class, false);
        valueDeserializer.addTrustedPackages("com.hoanghnt.swiftpay.event");
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionCompletedEvent>
            transactionCompletedKafkaListenerContainerFactory(
                    ConsumerFactory<String, TransactionCompletedEvent> transactionCompletedConsumerFactory,
                    org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TransactionCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transactionCompletedConsumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentSucceededEvent> paymentSucceededConsumerFactory() {
        JsonDeserializer<PaymentSucceededEvent> valueDeserializer =
                new JsonDeserializer<>(PaymentSucceededEvent.class, false);
        valueDeserializer.addTrustedPackages("com.hoanghnt.swiftpay.event");
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSucceededEvent>
            paymentSucceededKafkaListenerContainerFactory(
                    ConsumerFactory<String, PaymentSucceededEvent> paymentSucceededConsumerFactory,
                    org.springframework.kafka.listener.DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentSucceededEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentSucceededConsumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

}
