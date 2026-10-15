package com.chinacreator.gzcm.common.event;

import java.util.Map;

/**
 * 详细设计说明书-从零实施版 §3.3 {@code listEventTopics}（L2127）与 §3.3.2
 * {@code EventBusService / DltConsumer} 行（L2138「DLQ 命名 {@code <topic>.DLT} 并暴露 lag 指标」）
 * 所需的 <b>topic 滞后指标读取契约</b>。
 *
 * <p><b>为什么接口落在 common-api 而不是 runtime-event</b>：本仓既定口径是
 * 「gateway 编译期不硬依赖 runtime-event」（{@code gateway/SysConfigController} L40-41 已用反射
 * 查 {@code EventBusService} 并写明该理由）。若把本接口定义在 runtime-event，则
 * {@code SystemEventTopicsV1Controller} 要么破坏该口径、要么改用反射取值。
 * 接口下沉到 {@code common-api}（与 {@link KafkaTopics} 同包，符合「KafkaTopics 契约常量保留在
 * runtime/common-api」的 P8-A 收尾口径），实现留在 runtime-event（IR06：Kafka 访问一律收敛
 * runtime 底座，禁各模块自建 producer/consumer/AdminClient）。</p>
 *
 * <p><b>实现方必须遵守的诚实口径</b>：只返回<b>真实取到</b>的 topic→lag 条目。
 * 取不到（broker 不可达、消费者组未注册、AdminClient 超时）就<b>少返回或不返回</b>，
 * 禁以 {@code 0} 冒充「无滞后」——{@code lag=0} 与「无数据」在告警面上语义完全不同，
 * 前者会掩盖积压。调用方（端点）对缺失键一律呈现为 {@code null} 而非 0。</p>
 *
 * <p><b>装配面</b>：唯一实现 {@code KafkaAdminTopicLagSource}（runtime-event）按
 * {@code ecos.event.kafka.enabled=true} 条件装配，与 {@code KafkaEventBusServiceImpl}
 * 同一开关口径；默认（未配置）容器内无本类型 Bean ⇒ 端点走「指标未接」降级分支。</p>
 */
public interface TopicLagSource {

    /**
     * 一次性快照：base topic（不含 {@code .DLT} 后缀）→ 该 topic 全部消费者组合计滞后量。
     *
     * <p>键为业务 topic 名（与 {@link KafkaTopics} 常量值同形），值为跨该 topic 所有 partition、
     * 所有 consumer group 求和后的 lag。返回空 Map 表示「本次未取到任何指标」，
     * 不表示「全部 topic 无积压」。</p>
     */
    Map<String, Long> snapshotLag();

    /**
     * 指标来源标识（端点原样透出，供诊断页区分「真值」与「未接」）。
     * 约定值：{@code KAFKA_ADMIN}（AdminClient 实取）；实现亦可返回自定义标识。
     */
    default String sourceId() {
        return "UNKNOWN";
    }
}
