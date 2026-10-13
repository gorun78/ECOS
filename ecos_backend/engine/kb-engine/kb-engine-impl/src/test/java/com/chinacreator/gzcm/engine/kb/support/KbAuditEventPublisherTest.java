package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * F04-15 复盘收口 — {@link KbAuditEventPublisher} 验收（复刻 {@code KbAuditChainTest} 对
 * KnowledgeNavSecurityEngineClient#audit 先例的三条断言，六处 log-only 伪审计位点统一走本发布器）。
 *
 * <ol>
 *   <li>正常路径：publish 恰发一次 {@code KafkaTopics.AUDIT}，event 六字段齐
 *       （eventId/timestamp/userId/tenantId/action/resource/attr）；</li>
 *   <li>eventBus 未装配 → 本地兜底后 fail-closed 抛 ECOS-KB-022（禁静默丢审计）；</li>
 *   <li>publish 抛异常 → 同 ECOS-KB-022（F05-06 缺陷族：发布失败语义沿用先例，不新造）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KbAuditEventPublisherTest {

    @Mock
    private EventBusService eventBus;

    private static KbAuditEventPublisher publisher(EventBusService bus) {
        return new KbAuditEventPublisher(bus);
    }

    @Test
    @DisplayName("①正常路径：audit 经 EventBus 发 KafkaTopics.AUDIT，六字段齐")
    void auditPublishesToEventBus() {
        lenient().when(eventBus.isKafkaAvailable()).thenReturn(true);
        KbAuditEventPublisher p = publisher(eventBus);

        p.audit("kg_sync_full_submit", "job-1", "kg-sync");

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCap = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(topicCap.capture(), payloadCap.capture());
        assertEquals(KafkaTopics.AUDIT, topicCap.getValue());

        Object payload = payloadCap.getValue();
        assertInstanceOf(Map.class, payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> evt = (Map<String, Object>) payload;
        assertNotNull(evt.get("eventId"));
        assertNotNull(evt.get("timestamp"));
        assertNotNull(evt.get("userId"));
        assertNotNull(evt.get("tenantId"));
        assertEquals("kg_sync_full_submit", evt.get("action"));
        assertEquals("kg-sync", evt.get("resource"));
        assertEquals("job-1", evt.get("attr"));
    }

    @Test
    @DisplayName("②eventBus 未装配 → fail-closed 抛 ECOS-KB-022")
    void missingEventBusFailsClosed() {
        KbAuditEventPublisher p = publisher(null);

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> p.audit("kg_sync_object_submit", "job-1:OBJECT_A", "kg-sync"));
        assertEquals("ECOS-KB-022", ex.getCode());
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    @DisplayName("③publish 抛异常 → 亦 fail-closed 抛 ECOS-KB-022（不静默吞）")
    void publishThrowsFailsClosed() {
        lenient().when(eventBus.isKafkaAvailable()).thenReturn(false);
        doThrow(new RuntimeException("broker down")).when(eventBus).publish(anyString(), any());
        KbAuditEventPublisher p = publisher(eventBus);

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> p.audit("kb_doc_ingest", Map.of("docId", "d-1"), "kb-doc-ingest"));
        assertEquals("ECOS-KB-022", ex.getCode());
    }
}
