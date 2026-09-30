package com.chinacreator.gzcm.runtime.eventbus;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * runtime-event 类型化 Kafka producer 装配（PMO-74 H2-T4 收敛）。
 *
 * <p>背景：{@link KafkaEventBusServiceImpl} 构造器要求 {@code KafkaTemplate<String, String>}，
 * 但 Spring Kafka AutoConfiguration 只提供 {@code KafkaTemplate<Object, Object>}（泛型不变性），
 * 若再叠加 auto-config 会产双 KafkaTemplate 二义性 —— 故由本底座配置显式供给类型化模板，
 * 事件通道对 Kafka 客户端的依赖严格收敛于 runtime-event 一处（铁律 §2.5）。</p>
 *
 * <p>激活条件：{@code ecos.event.kafka.enabled=true} 且 spring-kafka 在 classpath 时才产 Bean，
 * 未启用态零 Bean 零连接（与 {@link KafkaEventBusServiceImpl} 一致，避免内存 fallback 模式误装配）。</p>
 */
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class KafkaBusProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaBusProducerConfig.class);

    @Bean
    @ConditionalOnMissingBean(name = "runtimeEventKafkaProducerFactory")
    public DefaultKafkaProducerFactory<String, String> runtimeEventKafkaProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        log.info("[runtime-event] Kafka producer 装配: bootstrap={}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, String> runtimeEventKafkaTemplate(
            DefaultKafkaProducerFactory<String, String> runtimeEventKafkaProducerFactory) {
        return new KafkaTemplate<>(runtimeEventKafkaProducerFactory);
    }
}
