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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.hoanghnt.swiftpay.event.UserRegisteredEvent;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:wallet-provisioning}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory() {
        JsonDeserializer<UserRegisteredEvent> valueDeserializer =
                new JsonDeserializer<>(UserRegisteredEvent.class, false);
        valueDeserializer.addTrustedPackages("com.hoanghnt.swiftpay.event");
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, groupId,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class),
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent>
            userRegisteredKafkaListenerContainerFactory(
                    ConsumerFactory<String, UserRegisteredEvent> userRegisteredConsumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, UserRegisteredEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userRegisteredConsumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
