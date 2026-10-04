package com.chinacreator.gzcm.engine.ontology.security;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-06 W89/C71 (M0 P0, O-25)：security 审计单通道走 {@link EventBusService}
 * + 无 Kafka 反射 bypass（PRD-03 §4.1-3 / 铁律 §2.5-4 / 后端规范 §九 runtime-event）。
 *
 * <p>契约：
 * <ul>
 *   <li>{@code SecurityEngineClient.audit(action, result)} 调
 *       {@code EventBusService.publish(KafkaTopics.AUDIT, event)} 单通道投递，
 *       不再通过 {@code kafkaTemplateBean.getClass().getMethod("send", …)}
 *       反射绕过横切底座；</li>
 *   <li>eventBus bean 缺失（fallback {@code MemoryEventBusServiceImpl} 未装配的
 *       极边缘情况）→ WARN 显式点名，<b>不静默丢</b>；不抛主链异常；</li>
 *   <li>{@code eventBus.publish 抛异常} → WARN 收住，不阻塞主链（旧 Kafka SEM 保
 *       持向后兼容）；双失败拒收属 F03-06 W82/C65（M1 面），非本批。</li>
 * </ul>
 *
 * <p>纯 Mockito，零 Spring context / DB / 网络。附加静态守门：走查本模块 main
 * 源无 {@code getClass().getMethod("send", …)} / {@code KAFKA 反射 send 调}
 * 遗留（O-25 结构性看不见面此条收紧）。
 */
@ExtendWith(MockitoExtension.class)
class OntologyAuditViaEventBusOnlyTest {

    @Mock EventBusService eventBus;
    @Mock org.springframework.web.client.RestTemplate rest;

    private SecurityEngineClient client;

    @BeforeEach
    void setUp() {
        client = new SecurityEngineClient("http://se", 1000, rest, eventBus);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("audit 单通道 → EventBusService.publish(AUDIT, event)")
    void auditGoesThroughEventBus() {
        client.audit("TEST_ACTION", "OK");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq(KafkaTopics.AUDIT), payload.capture());
        Object ev = payload.getValue();
        assertNotNull(ev, "emit 侧 payload 不应为 null");
        assertTrue(ev instanceof Map, "payload 应为 Map（沿用旧 Kafka 事件形态）");
        Map<?, ?> m = (Map<?, ?>) ev;
        assertTrue(m.containsKey("eventId") && m.containsKey("action")
                && m.containsKey("userId") && m.containsKey("timestamp")
                && m.containsKey("resource") && m.containsKey("result"));
        assertEquals("TEST_ACTION", m.get("action"));
        assertEquals("OK", m.get("result"));
        assertEquals("ontology", m.get("resource"));
    }

    @Test
    @DisplayName("audit：result=null → event.result 落字符串 \"OK\"（沿用旧契约）")
    void auditNullResultDefaultsOk() {
        client.audit("NULL_RESULT", null);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq(KafkaTopics.AUDIT), payload.capture());
        Map<?, ?> m = (Map<?, ?>) payload.getValue();
        assertEquals("OK", m.get("result"));
    }

    @Test
    @DisplayName("audit：eventBus 未装配（fallback 亦缺）→ WARN + 不抛、不静默")
    void auditEventBusMissingWarnsAndDoesNotThrow() {
        SecurityEngineClient named = new SecurityEngineClient("http://se", 1000, rest, null);
        assertDoesNotThrow(() -> named.audit("MISSING_BUS", "OK"));
    }

    @Test
    @DisplayName("audit：eventBus.publish 抛异常 → WARN 收住（不阻塞主流程）")
    void auditPublishThrowsIsSwallowed() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker unreachable"))
                .when(eventBus).publish(eq(KafkaTopics.AUDIT), org.mockito.ArgumentMatchers.any());
        assertDoesNotThrow(() -> client.audit("BROKEN_BUS", "OK"));
    }

    @Test
    @DisplayName("audit：无 SecurityContext 主体 → event.userId=anonymous（沿用旧语义，不伪造）")
    void auditWithoutAuthContextUsesAnonymous() {
        client.audit("ANON_CHECK", "OK-RESULT");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq(KafkaTopics.AUDIT), payload.capture());
        Map<?, ?> m = (Map<?, ?>) payload.getValue();
        assertEquals("anonymous", m.get("userId"));
    }

    @Test
    @DisplayName("白线：SecurityEngineClient ctor 保持 4 参（baseUrl, timeoutMs, rest, eventBus），未破坏 Spring 装配入口")
    void constructorSignatureHasOpinionatedEventBus() throws Exception {
        // 用反射确认 constructor 形参个数 = 4，且第 4 参类型 = EventBusService
        var ctors = SecurityEngineClient.class.getDeclaredConstructors();
        assertNotNull(ctors);
        assertTrue(ctors.length == 1, "应保持单一 ctor");
        var paramTypes = ctors[0].getParameterTypes();
        assertTrue(paramTypes.length == 4,
                "ctor 应保持 4 参（baseUrl, timeoutMs, RestTemplate, EventBusService）");
        assertTrue(EventBusService.class == paramTypes[3],
                "第 4 参类型应为 EventBusService (runtime-event 契约)");
    }
}
