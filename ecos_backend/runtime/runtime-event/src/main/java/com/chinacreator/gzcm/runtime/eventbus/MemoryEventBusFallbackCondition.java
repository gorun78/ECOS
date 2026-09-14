package com.chinacreator.gzcm.runtime.eventbus;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

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
 * @author ECOS-PMO
 * @since 1.0.0
 */
public class MemoryEventBusFallbackCondition implements Condition {

    private static final String KAFKA_BEAN_CLASS_NAME =
            "com.chinacreator.gzcm.runtime.eventbus.KafkaEventBusServiceImpl";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 已经注册了 KafkaEventBusServiceImpl bean → fallback 不启用
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
