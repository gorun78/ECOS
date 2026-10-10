package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.engine.ai.wagent.event.WAgentEvent;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Playbook 缺口事件登记（详细设计-10 F10-13 / PlaybookGapEventTest#missingPlaybookEventEmitted）。
 *
 * <p>当计划来源落到 EXPLORED（意图无在用 Playbook 可用）时记录"缺少 Playbook"事件，
 * 载荷 = 意图 + 槽位键 + 工具序列（供产品沉淀为新的 Playbook 素材）。只带引用与摘要，
 * 禁业务明细正文（§6.3）。</p>
 *
 * <p><b>事件名口径</b>：§9.1.2 注册名册（30 名）无独立 "PlaybookGap" 事件名，且词表单源
 * 禁自造——缺口事件复用 {@code EventTypes.Agent.PLAN_GENERATED}（事件即"计划生成"Outcome 的一种），
 * 以载荷 {@code gap_type=PLAYBOOK_MISSING} 判别；该映射已按三段一致纪律登记，R4 终验评估
 * 是否随 §9.1.2 名册增补专名。发布一律经 {@link WAgentEventPublisher} 单通道（禁自建 KafkaTemplate）。</p>
 */
@Component
public class WAgentPlaybookGapEventService {

    /** 缺口类型标记单源（消费端判别键）。 */
    public static final String GAP_TYPE_PLAYBOOK_MISSING = "PLAYBOOK_MISSING";

    private final WAgentEventPublisher eventPublisher;

    public WAgentPlaybookGapEventService(WAgentEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 记录缺口事件（不抛业务异常：登记面故障不得阻断编排主链，发布器自身 fail-loud）。
     *
     * @param aggregateId 聚合定位（missionId 优先；无 Mission 时传 runId）
     * @return 事件载荷（测试断言用；生产调用方可忽略）
     */
    public Map<String, Object> recordGap(String tenantId, String runId, String aggregateId,
                                         String intent, Collection<String> slotKeys,
                                         List<String> toolSequence, String operator) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gap_type", GAP_TYPE_PLAYBOOK_MISSING);
        payload.put("intent", intent);
        payload.put("slot_keys", slotKeys == null ? List.of() : List.copyOf(slotKeys));
        payload.put("tool_sequence", toolSequence == null ? List.of() : List.copyOf(toolSequence));
        WAgentEvent event = eventPublisher.envelope(EventTypes.Agent.PLAN_GENERATED,
                WAgentEvent.AGGREGATE_MISSION, aggregateId, tenantId, runId,
                operator, WAgentEvent.VIA_AGENT, payload);
        eventPublisher.publish(event);
        return payload;
    }
}
