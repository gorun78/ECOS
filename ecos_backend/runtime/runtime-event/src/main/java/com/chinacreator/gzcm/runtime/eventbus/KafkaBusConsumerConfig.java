package com.chinacreator.gzcm.runtime.eventbus;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * runtime-event 类型化 Kafka consumer 装配（PMO-74.2.1）。
 *
 * <p>与 {@link KafkaBusProducerConfig} 对偶：只供给<b>订阅基参</b>（base consumer properties），
 * 不产 {@code ConsumerFactory} Bean —— spring-kafka 3.1.2 的 {@code ContainerProperties}
 * 没有 {@code setGroupId}，group.id 只能来自 {@code ConsumerFactory}，而事件总线的订阅语义是
 * fan-out（每个订阅者都要收到全量事件，与 {@link MemoryEventBusServiceImpl} 的进程内广播一致），
 * 所以 {@link KafkaEventBusServiceImpl} 必须为每个订阅构造<b>独立 group</b> 的
 * {@code DefaultKafkaConsumerFactory}。共享 ConsumerFactory 会把订阅者变成竞争消费（load-balance），
 * 直接丢事件。
 *
 * <p>激活条件与 producer 侧完全一致（{@code ecos.event.kafka.enabled=true} + spring-kafka 在 classpath），
 * 未启用态零 Bean 零连接。
 *
 * <p>H2-T9（2026-09-29 实测）：基参以 {@link ConsumerBaseProps} 包装而非裸 {@code Map<String, Object>} ——
 * Spring 对 {@code Map<String, Object>} 形式的构造器参数按「多元素依赖」解析成
 * {@code beanName → bean} 集合，{@code @Qualifier} 只参与候选过滤、不会退回单 Bean 解析，
 * 结果是 {@code DefaultKafkaConsumerFactory} 收到 {@code {runtimeEventConsumerBaseProps={...}}}
 * → {@code Invalid value null for configuration key.deserializer}，消费容器 100% 起不来
 * （复现见 {@code KafkaEventBusWiringTest}）。
 */
@Configuration
@ConditionalOnClass(ConcurrentMessageListenerContainer.class)
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true")
public class KafkaBusConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaBusConsumerConfig.class);

    @Bean(name = "runtimeEventConsumerBaseProps")
    @ConditionalOnMissingBean(name = "runtimeEventConsumerBaseProps")
    public ConsumerBaseProps runtimeEventConsumerBaseProps(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${ecos.event.kafka.auto-offset-reset:latest}") String autoOffsetReset) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        log.info("[runtime-event] Kafka consumer 基参装配: bootstrap={}, autoOffsetReset={}",
                bootstrapServers, autoOffsetReset);
        return new ConsumerBaseProps(props);
    }

    /** 订阅基参持有者 — 具名类型，规避 Spring 对泛型 Map 注入点的集合解析。 */
    public static final class ConsumerBaseProps {

        private final Map<String, Object> values;

        ConsumerBaseProps(Map<String, Object> values) {
            this.values = values;
        }

        /** 供 {@code DefaultKafkaConsumerFactory} 使用的基参副本来源（调用方自行拷贝再叠加 group.id）。 */
        public Map<String, Object> asMap() {
            return values;
        }
    }
}
