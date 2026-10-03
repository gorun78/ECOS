package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-11 / W08：traceId 全链贯穿 — 请求 → MDC → 响应体三处一致。
 * <p>不依赖 Spring 上下文：过滤器单步（可控 chain）、MDC（TraceContext）、
 * ApiResponse 序列化回填三段各断言。</p>
 */
class TraceIdPropagationTest {

    private static final String XRI = TraceContext.HEADER_NAME;

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void filterPassesInboundTraceIdThroughAndClearsMdcAfterCompletion() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(XRI, "6f0d4a1b-2c3d-4e5f-9a8b-0123456789ab");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] traceInChain = new String[1];
        filter.doFilter(req, res, (s, r) -> traceInChain[0] = TraceContext.current());

        assertEquals("6f0d4a1b-2c3d-4e5f-9a8b-0123456789ab", res.getHeader(XRI));
        assertEquals("6f0d4a1b-2c3d-4e5f-9a8b-0123456789ab", traceInChain[0]);
        assertNull(TraceContext.current(), "请求结束必须清 MDC（防线程池串号）");
    }

    @Test
    void filterGeneratesTraceIdWhenHeaderMissing() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (s, r) -> {
        });
        String generated = res.getHeader(XRI);
        assertTrue(generated != null && generated.length() >= 8 && generated.length() <= 36,
                "自动生成 traceId 必须 8~36 位，实际: " + generated);
    }

    @Test
    void filterRegeneratesInvalidTraceIdWithoutRewritingInboundHeader() throws Exception {
        RequestContextFilter filter = new RequestContextFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(XRI, "abc"); // 短于 8 位 → 必须重新生成
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (s, r) -> {
        });
        String out = res.getHeader(XRI);
        assertTrue(out != null && out.length() >= 8, "非法入参必须重新生成");
        assertEquals("abc", req.getHeader(XRI), "原始请求头不被改写");
    }

    @Test
    void apiResponseBackfillsTraceIdFromMdcOnSerialization() throws Exception {
        TraceContext.put("trace-serialization-001");
        ApiResponse<Object> ok = ApiResponse.success("payload");
        JsonNode node = new ObjectMapper().readTree(ok.toJson());
        assertEquals("trace-serialization-001", node.path("traceId").asText());
        assertTrue(node.has("code") && node.has("timestamp"), "既有字段签名不变");
    }

    @Test
    void explicitTraceIdWinsOverMdc() {
        TraceContext.put("mdc-value");
        ApiResponse<Object> rsp = ApiResponse.success(null);
        rsp.setTraceId("explicit-value");
        assertEquals("explicit-value", rsp.getTraceId());
    }

    @Test
    void apiResponseOmitsTraceIdWhenAbsent() throws Exception {
        TraceContext.clear();
        ApiResponse<Object> rsp = ApiResponse.success(null);
        assertFalse(new ObjectMapper().readTree(rsp.toJson()).has("traceId"),
                "@JsonInclude(NON_NULL)：MDC 无值时字段缺席而非 null");
    }
}
