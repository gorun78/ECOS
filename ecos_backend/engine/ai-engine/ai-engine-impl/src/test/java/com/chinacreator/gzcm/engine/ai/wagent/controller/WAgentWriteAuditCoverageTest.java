package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.ai.audit.AiSecurityEngineAuditInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批D「审计留痕」的锚点覆盖面门禁（L3 四场景全拒必须留痕）。
 *
 * <p><b>缺陷背景</b>：{@code AiSecurityEngineAuditInterceptor} 原 scope 只含
 * {@code engine.ai.controller}，而 L3 发布/提交/凭证签发全在
 * {@code engine.ai.wagent.controller}。实测审计表 {@code resource LIKE 'WAgent%'} = 0 行，
 * 即「拒了但没记录」——批D 验收无从成立。</p>
 *
 * <p><b>钉四件事</b>：① wagent 写端点必发审计；② 载荷键与白名单逐字一致且携 traceId；
 * ③ 拒单（4xx）同样留痕且 result=FAIL；④ GET 不发（避免全读面风暴）。</p>
 */
class WAgentWriteAuditCoverageTest {

    /** 探测用 handler —— 本测试包即 {@code engine.ai.wagent.controller}，满足 scope。 */
    public static class Probe {
        public void handle() { }
    }

    private ApplicationContext context;
    private com.chinacreator.gzcm.runtime.eventbus.EventBusService bus;
    private AiSecurityEngineAuditInterceptor interceptor;
    private HandlerMethod handler;

    @BeforeEach
    void setUp() throws Exception {
        bus = mock(com.chinacreator.gzcm.runtime.eventbus.EventBusService.class);
        context = mock(ApplicationContext.class);
        when(context.getBeanNamesForType(com.chinacreator.gzcm.runtime.eventbus.EventBusService.class))
                .thenReturn(new String[]{"kafkaEventBusServiceImpl"});
        when(context.getBean(anyString())).thenReturn(bus);
        interceptor = new AiSecurityEngineAuditInterceptor(context);
        handler = new HandlerMethod(new Probe(), Probe.class.getMethod("handle"));
        MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, "trace-wagent-fixed");
    }

    @AfterEach
    void tearDown() {
        MDC.remove(EcosTraceContext.MDC_KEY_TRACE_ID);
    }

    @Test
    @DisplayName("wagent 发布/提交写端点必发审计，载荷携同源 traceId（批D 留痕）")
    void wagentWriteIsAuditedWithTraceId() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/wagent/candidates/c-1/publish");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);

        Map<String, Object> event = capturePublish();
        assertEquals("POST /api/v1/wagent/candidates/c-1/publish", event.get("action"));
        assertEquals("Probe#handle", event.get("resource"));
        assertEquals("OK", event.get("result"));
        assertEquals(200, event.get("statusCode"));
        assertEquals("trace-wagent-fixed", event.get("traceId"),
                "审计载荷必须携带入口侧 traceId（消费端不回填缺失链路标识）");
        assertEquals(new HashSet<>(AiSecurityEngineAuditInterceptor.auditPayloadKeyWhitelist()),
                event.keySet(), "审计载荷键必须与白名单一致: " + event.keySet());
    }

    @Test
    @DisplayName("拒单（403/409）同样留痕且 result=FAIL —— 「拒了没记录」不可接受")
    void rejectedWriteStillAudited() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/wagent/actions/a-1/commit");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(409);

        interceptor.afterCompletion(req, resp, handler, null);

        Map<String, Object> event = capturePublish();
        assertEquals("FAIL", event.get("result"));
        assertEquals(409, event.get("statusCode"));
    }

    @Test
    @DisplayName("PATCH 也纳入写面（补齐 X-11 同族漏网）")
    void patchIsWrite() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("PATCH");
        req.setRequestURI("/api/v1/wagent/candidates/c-1");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);

        assertEquals("PATCH /api/v1/wagent/candidates/c-1", capturePublish().get("action"));
    }

    @Test
    @DisplayName("GET 不发审计（读面风暴）")
    void readIsNotAudited() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/wagent/candidates/c-1");

        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);

        verify(bus, never()).publish(any(), any());
    }

    @Test
    @DisplayName("EventBus 未装配 ⇒ 不发不炸（不阻塞业务主流程）")
    void missingBusDoesNotBreakRequest() {
        when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/wagent/candidates");
        interceptor.afterCompletion(req, new MockHttpServletResponse(), handler, null);

        verify(bus, never()).publish(any(), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePublish() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(bus, times(1)).publish(eq(KafkaTopics.AUDIT), captor.capture());
        Map<String, Object> event = (Map<String, Object>) captor.getValue();
        assertNotNull(event.get("eventId"), "消费端以 eventId 派生确定性主键，缺则整条丢弃");
        return event;
    }
}
