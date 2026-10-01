package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.security.KnowledgeNavSecurityEngineClient;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * F04-15 审计事件验收：kb-nav 写操作审计经 EventBusService 发 KafkaTopics.AUDIT。
 *
 * <p>两个断言：
 * <ol>
 *   <li>{@link #extractWriteEmitsAuditEvent()} — 正常路径：写已成功，audit 恰发一次
 *       EventBusService.publish(KafkaTopics.AUDIT, event)；event 携带 eventId/userId/tenantId/
 *       action/resource/attr 六字段。</li>
 *   <li>{@link #eventBusUnavailableFailsClosed()} — eventBus 不可用（Spring 未装配
 *       或 publish 抛异常）→ 必须 fail-closed 抛 ECOS-KB-022
 *       {@link KbErrorCodes.DomainException}，绝不静默吞掉审计。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KbAuditChainTest {

    private KnowledgeNavSecurityEngineClient client;

    @Mock protected EventBusService eventBus;

    @BeforeEach
    void setUp() {
        lenient().when(eventBus.isKafkaAvailable()).thenReturn(true);
        client = buildClient(eventBus);
    }

    private static KnowledgeNavSecurityEngineClient buildClient(EventBusService bus) {
        return new KnowledgeNavSecurityEngineClient("http://localhost:18081", 5000,
                (RestTemplate) null, bus);
    }

    @Test
    @DisplayName("F04-15 ①：写成功 → 审计经 EventBus 发 KafkaTopics.AUDIT，6 字段齐")
    void extractWriteEmitsAuditEvent() {
        Map<String, Object> attr = new HashMap<>();
        attr.put("id", "cat-1");
        attr.put("domain", "finance");

        client.audit("kb.category.create", attr);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventBus).publish(topicCaptor.capture(), payloadCaptor.capture());
        assertEquals(KafkaTopics.AUDIT, topicCaptor.getValue(), "topic = KafkaTopics.AUDIT");

        Object payload = payloadCaptor.getValue();
        assertTrue(payload instanceof Map, "audit payload = Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> evt = (Map<String, Object>) payload;
        assertNotNull(evt.get("eventId"), "eventId 非空");
        assertNotNull(evt.get("timestamp"), "timestamp 非空");
        assertNotNull(evt.get("userId"), "userId 非空（ServiceContext/回退）");
        assertNotNull(evt.get("tenantId"), "tenantId 非空（TenantContextHolder/回退）");
        assertEquals("kb.category.create", evt.get("action"), "action 透传");
        assertEquals("kb-nav", evt.get("resource"), "resource 固定 kb-nav");
        assertEquals(attr, evt.get("attr"), "attr 原样下发");
    }

    @Test
    @DisplayName("F04-15 ②：eventBus 缺失 → fail-closed 抛 ECOS-KB-022")
    void eventBusUnavailableFailsClosed() {
        // 场景 A：eventBus == null（Spring 未装配 EventBusService Bean）
        KnowledgeNavSecurityEngineClient noBus = buildClient(null);

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> noBus.audit("kb.category.create", Map.of("id", "cat-1")));
        assertEquals("ECOS-KB-022", ex.getCode(), "事件底座不可用 = ECOS-KB-022 (EVENT_BASE_UNAVAILABLE)");
        assertEquals(500, ex.getHttpStatus(), "httpStatus=500");
    }

    @Test
    @DisplayName("F04-15 ③：publish 抛异常 → 亦 fail-closed 抛 ECOS-KB-022")
    void publishThrowsFailsClosed() {
        doThrow(new RuntimeException("broker down")).when(eventBus).publish(anyString(), any());

        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> client.audit("kb.category.create", Map.of("id", "cat-1")));
        assertEquals("ECOS-KB-022", ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("publish"),
                "message 应含 publish 失败原因");
    }
}
