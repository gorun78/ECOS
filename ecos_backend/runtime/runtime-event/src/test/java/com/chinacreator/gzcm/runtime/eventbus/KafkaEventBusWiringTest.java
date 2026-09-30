package com.chinacreator.gzcm.runtime.eventbus;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2-T9 接线回归 — 订阅基参必须<b>真的</b>到达 {@link KafkaEventBusServiceImpl} 的构造器。
 *
 * <p>缺陷形态（2026-09-29 dccheng 实测）：基参以裸 {@code Map<String, Object>} 作为构造器注入点时，
 * Spring 先按「多元素依赖」解析 → 实得 {@code {runtimeEventConsumerBaseProps={...}}}（beanName → bean），
 * {@code @Qualifier} 只参与候选过滤、不会退回单 Bean 解析；于是
 * {@code DefaultKafkaConsumerFactory} 收到 {@code Invalid value null for configuration key.deserializer}，
 * 消费容器 100% 起不来。修复 = 换用具名持有者 {@link KafkaBusConsumerConfig.ConsumerBaseProps}。</p>
 *
 * <p>门禁自证（§9.18 N-5）：三个用例分别是「基参 Bean 自身」（已知为真，对照组）/
 * 「裸 Map 注入点」的缺陷语义钉死 / 「持有者注入点」的修复验收，缺一即失去鉴别力。</p>
 */
class KafkaEventBusWiringTest {

    private static final String BASE_PROPS_BEAN = "runtimeEventConsumerBaseProps";

    /** 缺陷语义探针：保持修复前的注入形态，钉死 Spring 对泛型 Map 注入点的集合解析。 */
    static class RawMapProbe {
        final Map<String, Object> injected;

        RawMapProbe(@Qualifier(BASE_PROPS_BEAN) Map<String, Object> injected) {
            this.injected = injected;
        }
    }

    /** 修复后注入形态探针：与 {@link KafkaEventBusServiceImpl} 构造器同签名。 */
    static class HolderProbe {
        final KafkaBusConsumerConfig.ConsumerBaseProps injected;

        HolderProbe(@Qualifier(BASE_PROPS_BEAN) KafkaBusConsumerConfig.ConsumerBaseProps injected) {
            this.injected = injected;
        }
    }

    private AnnotationConfigApplicationContext contextWithSwitchOn() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> switchProps = new HashMap<>();
        switchProps.put("ecos.event.kafka.enabled", "true");
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", switchProps));
        ctx.register(KafkaBusConsumerConfig.class, RawMapProbe.class, HolderProbe.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @SuppressWarnings("unchecked")
    void basePropsBeanItselfCarriesDeserializers() {
        try (AnnotationConfigApplicationContext ctx = contextWithSwitchOn()) {
            KafkaBusConsumerConfig.ConsumerBaseProps bean =
                    (KafkaBusConsumerConfig.ConsumerBaseProps) ctx.getBean(BASE_PROPS_BEAN);
            Map<String, Object> props = bean.asMap();
            assertTrue(props.containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG), "基参 Bean 应含 bootstrap");
            assertTrue(props.containsKey(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG), "基参 Bean 应含 key.deserializer");
            assertTrue(props.containsKey(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG), "基参 Bean 应含 value.deserializer");
        }
    }

    @Test
    void rawMapInjectionPointYieldsBeanNameKeyedCollectionNotBaseProps() {
        try (AnnotationConfigApplicationContext ctx = contextWithSwitchOn()) {
            RawMapProbe probe = ctx.getBean(RawMapProbe.class);
            assertEquals(Set.of(BASE_PROPS_BEAN), probe.injected.keySet(),
                    "裸 Map 注入点必须是 beanName → bean 集合；若此项变化，说明 Spring 解析语义变了，需重评持有者方案");
            assertTrue(!probe.injected.containsKey(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG),
                    "裸 Map 注入点拿不到 key.deserializer —— 这正是 H2-T9 的根因，禁止回退到该形态");
        }
    }

    @Test
    void typedHolderInjectionPointDeliversBaseProps() {
        try (AnnotationConfigApplicationContext ctx = contextWithSwitchOn()) {
            HolderProbe probe = ctx.getBean(HolderProbe.class);
            assertTrue(probe.injected.asMap().containsKey(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG),
                    "持有者注入点必须把基参原样送进构造器");
        }
    }
}
