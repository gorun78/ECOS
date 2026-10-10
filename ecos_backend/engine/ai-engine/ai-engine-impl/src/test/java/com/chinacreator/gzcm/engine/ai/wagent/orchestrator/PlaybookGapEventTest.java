package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.engine.ai.wagent.event.WAgentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Playbook 缺口事件（详细设计-10 F10-13，冻结验收名）。
 *
 * <p>无在用 Playbook 走 EXPLORED 面 ⇒ 经 {@link WAgentEventPublisher} 单通道发布
 * {@code PLAN_GENERATED} 事件、载荷 {@code gap_type=PLAYBOOK_MISSING}（名册无专名，
 * 判别键单源；只带意图/槽位键/工具序列，禁业务明细正文，§6.3）。</p>
 */
class PlaybookGapEventTest {

    /** envelope 桩：按生产同构组装真实 WAgentEvent，供发布面断言。 */
    private static WAgentEvent buildEnvelope(Object[] args) {
        WAgentEvent event = new WAgentEvent((String) args[0], "test-source",
                (String) args[1], (String) args[2], args[7]);
        event.setTenantId((String) args[3]);
        event.setRunId((String) args[4]);
        event.setTraceId("trace-test");
        event.setActor(new WAgentEvent.Actor((String) args[5], (String) args[6]));
        return event;
    }

    @Test
    @DisplayName("F10-13：无 Playbook ⇒ 缺口事件经发布单通道出门，载荷含 gap_type/intent/slot_keys/tool_sequence")
    void missingPlaybookEventEmitted() {
        WAgentEventPublisher publisher = mock(WAgentEventPublisher.class);
        when(publisher.envelope(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> buildEnvelope(inv.getArguments()));

        WAgentPlaybookGapEventService service = new WAgentPlaybookGapEventService(publisher);
        Map<String, Object> payload = service.recordGap("t1", "r1", "m1", "FORECAST",
                List.of("metric", "as_of_date"), List.of("freeze_data_snapshot", "run_profit_forecast"), "u1");

        // 载荷判别键与摘要面（只引用不含正文）
        assertEquals(WAgentPlaybookGapEventService.GAP_TYPE_PLAYBOOK_MISSING,
                payload.get("gap_type"));
        assertEquals("FORECAST", payload.get("intent"));
        assertEquals(List.of("metric", "as_of_date"), payload.get("slot_keys"));
        assertEquals(List.of("freeze_data_snapshot", "run_profit_forecast"), payload.get("tool_sequence"));

        // 信封三要素：事件名取名册 PLAN_GENERATED、聚合 mission、aggregateId=m1；发布恰一次
        ArgumentCaptor<WAgentEvent> cap = ArgumentCaptor.forClass(WAgentEvent.class);
        verify(publisher).publish(cap.capture());
        WAgentEvent event = cap.getValue();
        assertEquals(EventTypes.Agent.PLAN_GENERATED, event.getEventType());
        assertEquals(WAgentEvent.AGGREGATE_MISSION, event.getAggregateType());
        assertEquals("m1", event.getAggregateId());
        assertEquals("t1", event.getTenantId());
        assertEquals("r1", event.getRunId());
        assertEquals(payload, event.getPayload());

        // 全链路：ExplorationPlanner 生成 EXPLORED 计划即触发缺口登记（同实例串接）
        WAgentPlaybookGapEventService wired = new WAgentPlaybookGapEventService(publisher);
        new ExplorationPlanner(wired).plan("t1", "r2", "m2", "DIAGNOSE",
                Map.of("metric", "profit"), "u1");
        ArgumentCaptor<WAgentEvent> cap2 = ArgumentCaptor.forClass(WAgentEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(cap2.capture());
        WAgentEvent chainEvent = cap2.getAllValues().get(1);
        assertEquals("m2", chainEvent.getAggregateId());
        assertEquals(WAgentPlaybookGapEventService.GAP_TYPE_PLAYBOOK_MISSING,
                ((Map<?, ?>) chainEvent.getPayload()).get("gap_type"));

        // 快捷八参发布面不得被缺口路径旁路使用（单通道纪律的负向哨兵）
        verify(publisher, never()).publish(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }
}
