package com.chinacreator.gzcm.runtime.eventbus.metrics;

import com.chinacreator.gzcm.common.event.TopicLagSource;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KafkaAdminTopicLagSource#aggregateLag} 的无 broker 口径门禁（#142/B4-1 指标源）。
 *
 * <p>lag 是 {@code listEventTopics} 唯一容易被冒充的字段：只要有人图省事把「取不到」写成 0，
 * 告警面就永远「无积压」。因此聚合算法必须是<b>纯函数</b>并能单独跑测——四条口径：
 * 正常差值、end 缺失跳过（禁补 0）、负值夹 0、内部 {@code __} topic 过滤。</p>
 */
class KafkaAdminTopicLagSourceTest {

    private static final TopicPartition AUDIT_P0 = new TopicPartition("ecos.audit", 0);
    private static final TopicPartition AUDIT_P1 = new TopicPartition("ecos.audit", 1);
    private static final TopicPartition ONTO_P0 = new TopicPartition("ecos.ontology", 0);

    @Test
    @DisplayName("正常口径：同 topic 多 partition 求和，跨 topic 分开")
    void sumsPerTopicAcrossPartitions() {
        Map<String, Long> lag = KafkaAdminTopicLagSource.aggregateLag(
                Map.of(AUDIT_P0, 100L, AUDIT_P1, 50L, ONTO_P0, 10L),
                Map.of(AUDIT_P0, 40L, AUDIT_P1, 50L, ONTO_P0, 4L));

        assertEquals(60L, lag.get("ecos.audit"), "(100-40) + (50-50) = 60");
        assertEquals(6L, lag.get("ecos.ontology"));
    }

    @Test
    @DisplayName("最新偏移缺失 ⇒ 该 partition 不参与，禁以 0 冒充「无积压」")
    void missingEndOffsetIsSkippedNotZeroFilled() {
        Map<String, Long> lag = KafkaAdminTopicLagSource.aggregateLag(
                Map.of(AUDIT_P0, 100L),
                Map.of(AUDIT_P0, 40L, ONTO_P0, 7L));

        assertEquals(60L, lag.get("ecos.audit"));
        assertFalse(lag.containsKey("ecos.ontology"),
                "取不到 end offset 的 topic 必须整个缺席（出现 0 就是假绿）");
    }

    @Test
    @DisplayName("committed 大于 end（rebalance/截断）⇒ 夹 0，不出现负 lag")
    void negativeLagIsClampedToZero() {
        Map<String, Long> lag = KafkaAdminTopicLagSource.aggregateLag(
                Map.of(AUDIT_P0, 10L),
                Map.of(AUDIT_P0, 25L));
        assertEquals(0L, lag.get("ecos.audit"), "真取到的差值为负时才允许 0");
    }

    @Test
    @DisplayName("内部 __consumer_offsets 一类 topic 不计入业务 lag")
    void internalTopicsAreFiltered() {
        Map<String, Long> lag = KafkaAdminTopicLagSource.aggregateLag(
                Map.of(new TopicPartition("__consumer_offsets", 0), 900L),
                Map.of(new TopicPartition("__consumer_offsets", 0), 100L));
        assertTrue(lag.isEmpty(), "内部 topic 不得出现在业务滞后指标里，实际=" + lag);
    }

    @Test
    @DisplayName("空/ null 入参 ⇒ 空快照（不是 NPE，也不是「全 0」）")
    void emptyInputsYieldEmptySnapshot() {
        assertTrue(KafkaAdminTopicLagSource.aggregateLag(Map.of(), Map.of(AUDIT_P0, 1L)).isEmpty());
        assertTrue(KafkaAdminTopicLagSource.aggregateLag(null, null).isEmpty());
    }

    @Test
    @DisplayName("条件装配：开关未开时不得有实现（gateway 侧 lag 因此如实为 null）")
    void sourceIsNotWiredWithoutKafkaSwitch() {
        // 该断言盯的是装配口径漂移：若有人去掉 @ConditionalOnProperty，
        // 默认档位（无 Kafka）也会建 AdminClient，只读诊断端点会开始连 broker。
        var ann = KafkaAdminTopicLagSource.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertNotNull(ann, "实现必须带 @ConditionalOnProperty，禁无条件装配");
        assertTrue(java.util.List.of(ann.name()).contains("ecos.event.kafka.enabled"),
                "条件键必须与 KafkaEventBusServiceImpl 同开关，实际=" + java.util.Arrays.toString(ann.name()));
        assertEquals("true", ann.havingValue());
        assertTrue(TopicLagSource.class.isAssignableFrom(KafkaAdminTopicLagSource.class));
    }
}
