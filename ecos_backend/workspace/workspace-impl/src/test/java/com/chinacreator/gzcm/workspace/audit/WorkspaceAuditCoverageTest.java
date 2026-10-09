package com.chinacreator.gzcm.workspace.audit;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 卷07 F07-18-2（W182 / C-6）验收 —— workspace 审计锚点覆盖面。
 *
 * <p>证明四件事：</p>
 * <ol>
 *   <li>敏感读（/completeness、/graph、/runs、/decisions、/actions、/cognition/*）审计；</li>
 *   <li>非敏感静态元数据（binding-catalog）不审计（避免全 GET 风暴）；</li>
 *   <li>场景列表/详情按 subject 每分钟 1 条抽样；</li>
 *   <li>载荷只带 ID 与摘要（键白名单，禁结果原文），并携带同源 traceId（F07-21）。</li>
 * </ol>
 */
class WorkspaceAuditCoverageTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<EventBusService> provider = mock(ObjectProvider.class);
    private final EventBusService bus = mock(EventBusService.class);
    private WorkspaceSecurityEngineAuditInterceptor interceptor;

    /** 探测用 handler —— 包名必须落在 workspace scope 内（本测试包即满足）。 */
    public static class Probe {
        public void handle() { }
    }

    private HandlerMethod handler;

    @BeforeEach
    void setUp() throws Exception {
        when(provider.getIfAvailable()).thenReturn(bus);
        interceptor = new WorkspaceSecurityEngineAuditInterceptor(provider);
        handler = new HandlerMethod(new Probe(), Probe.class.getMethod("handle"));
    }

    private void invoke(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setRequestURI(uri);
        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePublish() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(bus).publish(eq(KafkaTopics.AUDIT), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    @Test
    void sensitiveReadsAreAudited() {
        // C-6 敏感读：completeness → 审计，且载荷带同源 traceId
        invoke("GET", "/api/v1/workspace/scenarios/s-1/completeness");
        Map<String, Object> event = capturePublish();
        assertEquals("GET /api/v1/workspace/scenarios/s-1/completeness", event.get("action"));
        assertNotNull(event.get("traceId"), "F07-21：审计载荷必须携带 traceId");
        assertTrue(((String) event.get("traceId")).length() > 0);

        // 认知四端点 /cognition/* → 审计
        invoke("GET", "/api/v1/business/scenarios/s-1/cognition/detect");
        verify(bus, times(2)).publish(eq(KafkaTopics.AUDIT), any());

        // 非敏感静态元数据（binding-catalog）→ 不审计
        invoke("GET", "/api/v1/workspace/binding-catalog");
        verify(bus, times(2)).publish(eq(KafkaTopics.AUDIT), any());

        // 普通 GET（列表以外、非敏感后缀）→ 不审计
        invoke("GET", "/api/v1/workspace/scenarios/s-1/status");
        verify(bus, times(2)).publish(eq(KafkaTopics.AUDIT), any());

        // 写侧全量含 PATCH（X-11 补漏）→ 审计
        invoke("PATCH", "/api/v1/workspace/scenarios/s-1");
        Map<String, Object> patchEvent = lastPublish();
        assertEquals("PATCH /api/v1/workspace/scenarios/s-1", patchEvent.get("action"));
    }

    @Test
    void scenarioListAndDetailSampledOncePerSubjectMinute() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/workspace/scenarios");
        req.addHeader("X-User-Id", "u-sampler");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        interceptor.afterCompletion(req, resp, handler, null);
        interceptor.afterCompletion(req, resp, handler, null);
        // 同一 subject 同一分钟窗口：只发 1 条
        verify(bus, times(1)).publish(eq(KafkaTopics.AUDIT), any());

        // 不同 subject 各自有独立抽样窗口
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setMethod("GET");
        other.setRequestURI("/api/v1/workspace/scenarios/s-9");
        other.addHeader("X-User-Id", "u-other");
        interceptor.afterCompletion(other, resp, handler, null);
        verify(bus, times(2)).publish(eq(KafkaTopics.AUDIT), any());
    }

    @Test
    void auditPayloadCarriesIdsNotResultBody() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/workspace/scenarios/s-1/graph");
        Map<String, Object> event = interceptor.buildAuditEvent(req, 200, false, "ScenarioBindingLinkController#graph");

        assertEquals(new HashSet<>(WorkspaceSecurityEngineAuditInterceptor.auditPayloadKeyWhitelist()),
                event.keySet(), "审计载荷键必须与白名单一致（只带 ID 与摘要，禁结果原文）");
        assertEquals("OK", event.get("result"));
        assertEquals(200, event.get("statusCode"));

        // EventBus 未装配 → 不发不炸（WARN 留痕），主流程不受影响
        when(provider.getIfAvailable()).thenReturn(null);
        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);
        verify(bus, never()).publish(any(), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastPublish() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(bus, org.mockito.Mockito.atLeastOnce()).publish(eq(KafkaTopics.AUDIT), captor.capture());
        List<Object> all = captor.getAllValues();
        return (Map<String, Object>) all.get(all.size() - 1);
    }
}
