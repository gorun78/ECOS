package com.chinacreator.gzcm.runtime.eventbus;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * 内存 EventBus fallback 装配条件 — 容器里不存在 KafkaEventBusServiceImpl 时才激活。
 *
 * <p>实现选择：不直接 {@code @ConditionalOnMissingBean(KafkaEventBusServiceImpl.class)}
 * （该注解在 {@code @Configuration} 级别生效，对 {@code @Service} 扫描类不直接适用），
 * 改为手写 {@link Condition}：遍历 {@link ConditionContext#getBeanFactory()}'s
 * bean definition names 判断。
 *
 * <p>等价语义：当 {@code ecs.event.kafka.enabled=true} 且 spring-kafka 在 classpath 时，
 * {@link KafkaEventBusServiceImpl} 已注册为 Bean → 本条件 {@code false} → 内存 Bean 不加载；
 * 其余情况本条件 {@code true} → 内存 Bean 加载作为 fallback。
 *
 * <p><b>PMO-59 P2b 修复（2026-09-14，runtime 缺口补强，铁律 §2.5"补强而非自建"）</b>：
 * 原实现仅靠 beanDefinition 扫描判定，存在<b>组件扫描顺序依赖缺陷</b>——
 * {@link KafkaEventBusServiceImpl} 与 {@link MemoryEventBusServiceImpl} 同为
 * {@code @Service} 候选类且处于同一扫描包，jar 扫描序不保证 Kafka 先于 Memory 注册；
 * 当 Memory 先被扫描时本条件看不到 Kafka 定义 → 两实现同批注册 →
 * 消费方按单例注入报 "required a single bean, but 2 were found"（gateway 启动实证）。
 * 现增加<b>配置面短路</b>：当 {@code KafkaEventBusServiceImpl} 自身的类级激活条件
 * （{@code ecos.event.kafka.enabled=true} + spring-kafka 在 classpath）确定满足时，
 * 直接判定 false，与扫描顺序无关；beanDefinition 扫描保留作为防御层。
 * 该短路语义与 Kafka 实现声明面的激活条件完全一致（exists-if-and-only-if）。
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
public class MemoryEventBusFallbackCondition implements Condition {

    private static final String KAFKA_BEAN_CLASS_NAME =
            "com.chinacreator.gzcm.runtime.eventbus.KafkaEventBusServiceImpl";

    /** 与 {@link KafkaEventBusServiceImpl} 声明面一致的开关属性（双闸门之一） */
    private static final String KAFKA_ENABLED_PROPERTY = "ecos.event.kafka.enabled";

    /** spring-kafka 坐标探测类（双闸门之二，对应其 @ConditionalOnClass(KafkaTemplate.class)） */
    private static final String KAFKA_TEMPLATE_CLASS_NAME = "org.springframework.kafka.core.KafkaTemplate";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // PMO-59 P2b 配置面短路：Kafka 实现激活条件确定满足 → fallback 必然不启用（消除扫描顺序依赖）
        String kafkaEnabled = context.getEnvironment().getProperty(KAFKA_ENABLED_PROPERTY, "false");
        if ("true".equalsIgnoreCase(kafkaEnabled.trim())
                && ClassUtils.isPresent(KAFKA_TEMPLATE_CLASS_NAME, context.getClassLoader())) {
            return false;
        }
        // 已注册 KafkaEventBusServiceImpl bean → fallback 不启用
        String[] names = context.getBeanFactory().getBeanDefinitionNames();
        for (String name : names) {
            try {
                String className = context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
                if (KAFKA_BEAN_CLASS_NAME.equals(className)) {
                    return false;
                }
            } catch (Exception ignore) {
                // 某 bean 定义无法解析（abstract / 工厂方法未声明类型）时视为非目标，继续遍历
            }
        }
        return true;
    }
}
