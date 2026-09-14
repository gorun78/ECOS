package com.chinacreator.gzcm.runtime.eventbus;

import java.util.function.Consumer;

/**
 * 统一事件总线服务 — Kafka broker 在/不在 都能调。
 *
 * <p>位置说明（铁律 §2.5 公共底座）：
 * <ul>
 *   <li>本接口定义在 {@code runtime-event}，是 runtime 横切底座之一；</li>
 *   <li>{@code common-api} 仅保留 KafkaTopics 契约常量（不 import spring-kafka），
 *       避免污染契约层对 Kafka 客户端的依赖；</li>
 *   <li>跨模块事件通信统一走本接口发送/订阅，引擎/服务禁止各自 new KafkaTemplate。</li>
 * </ul>
 *
 * <p>降级策略（强制）：
 * <ul>
 *   <li>{@code spring.kafka.template} Bean 存在（由 Spring Kafka AutoConfiguration
 *       在 {@code spring.kafka.*} 配置存在时产出）且 {@code ecos.event.kafka.enabled=true}
 *       → 加载 {@link KafkaEventBusServiceImpl}（Kafka producer）。</li>
 *   <li>否则加载 {@link MemoryEventBusServiceImpl}（Spring in-process {@code ApplicationEventPublisher}
 *       同步分发），本地/开发/降级态。</li>
 *   <li>broker 不可用不阻塞启动：Kafka listener 由 Spring Kafka 内部退避重连，
 *       consumer 侧 {@code @KafkaListener} 由 dbus 侧用 {@code @ConditionalOnClass} + {@code
 *       @ConditionalOnProperty} 条件化装载。</li>
 * </ul>
 *
 * <p>使用示例（producer 侧，buszhi）：
 * <pre>{@code
 * EventBusService bus;
 * bus.publish(KafkaTopics.ONTOLOGY_PUBLISHED, event);   // Kafka 在 → 入 Kafka; 不在 → 进内存总线
 * }</pre>
 *
 * <p>使用示例（consumer 侧，dbus）：
 * <pre>{@code
 * bus.subscribe(KafkaTopics.ONTOLOGY_PUBLISHED, String.class, this::onJson);
 * }</pre>
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
public interface EventBusService {

    /**
     * 发布事件到指定 topic。
     *
     * @param topic   {@code common-api} 中的 topic 常量（如 {@code KafkaTopics.ONTOLOGY_PUBLISHED}）
     * @param payload 业务事件对象，Kafka 路径会 JSON 序列化（{@code ObjectMapper.writeValueAsString}）；
     *                内存路径原样分发
     */
    void publish(String topic, Object payload);

    /**
     * 编程式订阅（可选）。Kafka 路径下仅做内存镜像注册（listener 委托 Spring 自动装载）；
     * 内存路径下换算为 {@code ApplicationListener} 注册。
     *
     * @param topic       要订阅的 topic
     * @param payloadType 载荷反序列化类型（Kafka 路径用 JSON 反序列化目标类）
     * @param handler     处理回调
     */
    void subscribe(String topic, Class<?> payloadType, Consumer<Object> handler);

    /**
     * 健康查询：当前是否可用 Kafka 路径（producer 端 cluster 可达）。
     *
     * @return true=Kafka 可用；false=内存 fallback 或 broker 不可达（不抛异常，调用方据此打 warning）
     */
    boolean isKafkaAvailable();
}
