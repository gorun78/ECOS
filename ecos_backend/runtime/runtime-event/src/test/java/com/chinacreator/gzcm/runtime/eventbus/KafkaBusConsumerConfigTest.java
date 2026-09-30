package com.chinacreator.gzcm.runtime.eventbus;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KafkaBusConsumerConfig} 订阅基参用例（PMO-74 H2-T8）。
 *
 * <p>锁定两条不能被静默改掉的性质：反序列化器必须是 String（载荷策略=JSON 文本），
 * 以及<b>基参里不得出现 group.id</b> —— group 必须由
 * {@link KafkaEventBusServiceImpl#subscribe} 逐订阅生成，否则 fan-out 会退化成竞争消费丢事件。
 */
class KafkaBusConsumerConfigTest {

    private final KafkaBusConsumerConfig config = new KafkaBusConsumerConfig();

    @Test
    @DisplayName("基参：bootstrap/反序列化/自动提交按入参装配，且不含 group.id")
    void basePropsCarryBootstrapAndStringDeserializersWithoutGroupId() {
        Map<String, Object> props = config.runtimeEventConsumerBaseProps("kafka:9092", "earliest").asMap();

        assertEquals("kafka:9092", props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(StringDeserializer.class, props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(Boolean.TRUE, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals("earliest", props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));

        assertTrue(!props.containsKey(ConsumerConfig.GROUP_ID_CONFIG),
                "基参带 group.id 会把每个订阅独占 group 的 fan-out 语义改成竞争消费");
    }
}
