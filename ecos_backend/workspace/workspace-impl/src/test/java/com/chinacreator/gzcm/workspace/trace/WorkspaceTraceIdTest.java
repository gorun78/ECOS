package com.chinacreator.gzcm.workspace.trace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.workspace.audit.WorkspaceSecurityEngineAuditInterceptor;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.chinacreator.gzcm.workspace.harness.WorkspaceHarnessProbeController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 卷07 F07-21（W185）—— traceId 四段贯通验收（文档冻结验收标识
 * {@code mvn -Dtest=WorkspaceTraceIdTest#traceIdPresentInLogAuditResponseAndOutboundCall}）。
 *
 * <p>同一请求线程内四处必须同值：① 日志（logback appender 捕获 MDC）；
 * ② 响应体 {@code ApiResponse.traceId}；③ 出站 HTTP 头 {@code X-Request-Id}
 * （MockRestServiceServer 断言，替代 WireMock）；④ 审计载荷。
 * traceId 单源 = 分册 00 的 {@link EcosTraceContext}，workspace 不自建生成器。</p>
 */
class WorkspaceTraceIdTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("traceIdPresentInLogAuditResponseAndOutboundCall")
    void traceIdPresentInLogAuditResponseAndOutboundCall() throws Exception {
        String traceId = "trace-" + UUID.randomUUID();
        MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, traceId);
        Logger adviceLogger = (Logger) LoggerFactory.getLogger(WorkspaceExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        adviceLogger.addAppender(appender);
        try {
            // ② 响应体：advice 固定形态携带 traceId（错误态诊断码，F07-13 同源）
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkspaceHarnessProbeController())
                    .setControllerAdvice(new WorkspaceExceptionHandler())
                    .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                    .build();
            MvcResult res = mvc.perform(get("/api/v1/workspace/_harness/throw/internal")).andReturn();
            Map<?, ?> body = MAPPER.readValue(
                    res.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
            assertEquals(traceId, body.get("traceId"), "响应体 traceId 必须与 MDC 同源");

            // ① 日志面：advice 记录同一请求时 MDC 同值
            assertFalse(appender.list.isEmpty(), "advice 必须落日志（日志面可追踪）");
            assertEquals(traceId,
                    appender.list.get(0).getMDCPropertyMap().get(EcosTraceContext.MDC_KEY_TRACE_ID),
                    "日志 MDC traceId 必须与响应体同源");

            // ③ 出站 HTTP 头：拦截器盖 X-Request-Id（F07-17 收口后的统一出口）
            RestTemplate outbound = new RestTemplate();
            outbound.setInterceptors(List.of(new TraceIdClientInterceptor()));
            MockRestServiceServer server = MockRestServiceServer.bindTo(outbound).build();
            server.expect(requestTo("http://svc.test/api/v1/ping"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(EcosTraceContext.HEADER_REQUEST_ID, traceId))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
            outbound.getForObject("http://svc.test/api/v1/ping", String.class);
            server.verify();

            // ④ 审计载荷：X-58 修正 —— 载荷必须携带同源 traceId
            WorkspaceSecurityEngineAuditInterceptor audit =
                    new WorkspaceSecurityEngineAuditInterceptor(null);
            MockHttpServletRequest auditReq =
                    new MockHttpServletRequest("POST", "/api/v1/workspace/scenarios/s-1/run");
            Map<String, Object> event =
                    audit.buildAuditEvent(auditReq, 200, false, "ScenarioRunController#run");
            assertEquals(traceId, event.get("traceId"), "审计载荷 traceId 必须与四段同源");
        } finally {
            appender.stop();
            adviceLogger.detachAppender(appender);
            MDC.remove(EcosTraceContext.MDC_KEY_TRACE_ID);
        }
    }

    @Test
    @DisplayName("入站 X-Request-Id 继承进 MDC，请求结束清理（:18090 独立宿主面）")
    void inboundRequestIdHeaderBecomesTraceIdAndIsCleanedUp() throws Exception {
        // 同线程前序测试可能已经 currentTraceId() 惰性写入 MDC —— 本用例验证"入站继承"，须从干净 MDC 起
        MDC.remove(EcosTraceContext.MDC_KEY_TRACE_ID);
        WorkspaceTraceIdFilter filter = new WorkspaceTraceIdFilter();
        String inbound = "inbound-" + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(EcosTraceContext.HEADER_REQUEST_ID, inbound);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (rq, rs) -> seen.set(EcosTraceContext.currentTraceId()));
        assertEquals(inbound, seen.get(), "入站头必须被继承为 traceId，禁另造新值");
        assertNull(MDC.get(EcosTraceContext.MDC_KEY_TRACE_ID),
                "本过滤器绑定的 MDC 键必须在链路结束后清理（防线程池串染）");
    }

    @Test
    @DisplayName("调用点已显式带 X-Request-Id 时出站拦截器不覆盖")
    void outboundKeepsCallerProvidedHeader() {
        RestTemplate outbound = new RestTemplate();
        outbound.setInterceptors(List.of(new TraceIdClientInterceptor()));
        MockRestServiceServer server = MockRestServiceServer.bindTo(outbound).build();
        server.expect(requestTo("http://svc.test/api/v1/ping"))
                .andExpect(header(EcosTraceContext.HEADER_REQUEST_ID, "caller-owned"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        HttpHeaders headers = new HttpHeaders();
        headers.set(EcosTraceContext.HEADER_REQUEST_ID, "caller-owned");
        outbound.exchange("http://svc.test/api/v1/ping", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        server.verify();
    }
}
