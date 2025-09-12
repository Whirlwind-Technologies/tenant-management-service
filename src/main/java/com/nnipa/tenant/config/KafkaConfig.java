package com.nnipa.tenant.config;

import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for Tenant Management Service
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

    /**
     * Kafka Admin configuration for topic management
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Create topics for tenant events
     */
    @Bean
    public NewTopic tenantCreatedTopic() {
        return TopicBuilder.name("nnipa.events.tenant.created")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000") // 7 days
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic tenantUpdatedTopic() {
        return TopicBuilder.name("nnipa.events.tenant.updated")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic tenantActivatedTopic() {
        return TopicBuilder.name("nnipa.events.tenant.activated")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic tenantSuspendedTopic() {
        return TopicBuilder.name("nnipa.events.tenant.suspended")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic subscriptionCreatedTopic() {
        return TopicBuilder.name("nnipa.events.subscription.created")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic subscriptionChangedTopic() {
        return TopicBuilder.name("nnipa.events.subscription.changed")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic featureEnabledTopic() {
        return TopicBuilder.name("nnipa.events.feature.enabled")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic featureDisabledTopic() {
        return TopicBuilder.name("nnipa.events.feature.disabled")
                .partitions(6)
                .replicas(3)
                .config("retention.ms", "604800000")
                .config("compression.type", "snappy")
                .build();
    }

    /**
     * Producer configuration for byte array serialization
     */
    @Bean
    public ProducerFactory<String, byte[]> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * Kafka template for sending messages
     */
    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Consumer configuration for byte array deserialization
     */
    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configs.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configs.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return new DefaultKafkaConsumerFactory<>(configs);
    }

    /**
     * Listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Protobuf serializer for direct protobuf usage
     */
    @Bean
    public KafkaProtobufSerializer kafkaProtobufSerializer() {
        KafkaProtobufSerializer serializer = new KafkaProtobufSerializer();
        Map<String, Object> configs = new HashMap<>();
        configs.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        configs.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        serializer.configure(configs, false);
        return serializer;
    }

    /**
     * Protobuf deserializer for direct protobuf usage
     */
    @Bean
    public KafkaProtobufDeserializer kafkaProtobufDeserializer() {
        KafkaProtobufDeserializer deserializer = new KafkaProtobufDeserializer();
        Map<String, Object> configs = new HashMap<>();
        configs.put(KafkaProtobufDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        deserializer.configure(configs, false);
        return deserializer;
    }
}