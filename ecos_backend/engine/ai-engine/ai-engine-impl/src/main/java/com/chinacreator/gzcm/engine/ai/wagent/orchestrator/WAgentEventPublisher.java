package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.event.WAgentEvent;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * W Agent 域事件发布单通道（详细设计-10 §9.1 信封 + §6.1 事件纪律）。
 *
 * <p><b>禁自建 KafkaTemplate</b>：一律经 runtime-event {@link EventBusService}
 * （铁律 §2.5；Kafka 缺席时底座自动落内存总线）。topic 归属沿用
 * {@link KafkaTopics#topicForAggregate(String)} 在位路由表，本类不自立路由。</p>
 *
 * <p>发布前强制 {@link WAgentEvent#assertEnvelopeComplete()}（R-68②：五个 additive
 * 字段不齐不出门，禁半信封入 Kafka）。EventBus 未装配（如独立单测切片）时记 WARN 并跳过，
 * 禁伪称已投递。</p>
 */
@Component
public class WAgentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WAgentEventPublisher.class);

    /** 事件 source 单源（本引擎出发的全部 W 域事件同标识）。 */
    public static final String EVENT_SOURCE = "ai-engine:wagent";

    private final ObjectProvider<EventBusService> eventBusProvider;

    public WAgentEventPublisher(ObjectProvider<EventBusService> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /**
     * 组信封（traceId 与 MDC 同源、actor 默认 agent 触发、schema_version 由 {@link WAgentEvent} 默认）。
     *
     * @param via {@link WAgentEvent#VIA_AGENT}/{@code VIA_USER}/{@code VIA_SYSTEM} 之一
     */
    public WAgentEvent envelope(String eventType, String aggregateType, String aggregateId,
                                String tenantId, String runId, String userId, String via,
                                Object payload) {
        WAgentEvent event = new WAgentEvent(eventType, EVENT_SOURCE, aggregateType, aggregateId, payload);
        event.setTenantId(tenantId);
        event.setRunId(runId);
        event.setTraceId(AiTraceSupport.currentTraceId());
        event.setActor(new WAgentEvent.Actor(userId, via));
        return event;
    }

    /** 发送：先自检信封完整，再经 EventBusService 单通道按聚合名路由。 */
    public void publish(WAgentEvent event) {
        event.assertEnvelopeComplete();
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("EventBusService 未装配，W 域事件仅日志留痕（禁伪称已投递）: type={} aggregate={}",
                    event.getEventType(), event.getAggregateType());
            return;
        }
        bus.publish(KafkaTopics.topicForAggregate(event.getAggregateType()), event);
    }

    /** 组 + 发一步式快捷面（调用方缺省 via=agent）。 */
    public void publish(String eventType, String aggregateType, String aggregateId,
                        String tenantId, String runId, String userId, Object payload) {
        publish(envelope(eventType, aggregateType, aggregateId, tenantId, runId,
                userId, WAgentEvent.VIA_AGENT, payload));
    }
}
