package com.chinacreator.gzcm.workspace.trace;

import com.chinacreator.gzcm.common.context.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * F07-21 / C167 场景独立态 traceId 接力过滤器单测门禁（6 例，Mockito mock 两个 servlet 接口）。
 *
 * <p><b>证明两件事</b>：
 * <ol>
 *   <li><b>断链已补</b>：独立 :18090 态下（无 gateway 侧 {@code RequestContextFilter}）
 *       本 filter 把 X-Request-Id 入 MDC + 回响应头 + finally 清 MDC，
 *       {@link com.chinacreator.gzcm.workspace.QueryHistoryService} 落 V212 {@code trace_id}
 *       列时能拿到非空值（V212 允许空，但断链 = F07-21 "traceId 全链贯通" 只完成一半）。</li>
 *   <li><b>monolith 幂等</b>：gateway monolith 态（外层 filter 已设 MDC）下本 filter
 *       <b>不再生成、不覆盖、不清 MDC</b>，只回外层值到响应头 —— 清 MDC 权独占于
 *       最外层 filter，杜绝 "内层先清 → 外层 finally 拿到脏状态" / 双清 两种畸形。</li>
 * </ol>
 *
 * <p>不用 spring-test MockHttpServletRequest/Response（workspace-impl test 依赖面无
 * spring-test）—— 直接 Mockito mock 两个 servlet 接口即可覆盖 2 个用到的方法
 * ({@code getHeader} + {@code setHeader})。</p>
 */
class WorkspaceTraceIdFilterTest {

    private WorkspaceTraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new WorkspaceTraceIdFilter();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /** 简化版 servlet req 构造：只 stub 单一 getHeader；chain 捕获 handler 期间 MDC 值。 */
    private static HttpServletRequest reqWithHeader(String headerValue) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        org.mockito.Mockito.when(req.getHeader(TraceContext.HEADER_NAME)).thenReturn(headerValue);
        return req;
    }

    @Test
    @DisplayName("独立态·无 header → 生成 36 位 UUID 写入 MDC + 回响应头 + finally 清 MDC")
    void standalone_noHeader_generatesUuid_setsHeader_clearsAfter() throws Exception {
        HttpServletRequest req = reqWithHeader(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        final String[] during = new String[1];
        FilterChain chain = (r, w) -> during[0] = TraceContext.current();
        filter.doFilter(req, resp, chain);

        assertNotNull(during[0], "进入 handler 期间 MDC 应有 traceId");
        assertEquals(36, during[0].length(), "生成 traceId 应为 36 位 UUID 形态");
        assertNull(TraceContext.current(), "独立态由本 filter 拥有 → 结束后必清 MDC（防线程池串号）");

        ArgumentCaptor<String> hdr = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(org.mockito.ArgumentMatchers.eq(TraceContext.HEADER_NAME), hdr.capture());
        assertEquals(36, hdr.getValue().length(), "响应头应回带 36 位生成 traceId");
    }

    private static String eqHeaderName() {
        return TraceContext.HEADER_NAME;
    }

    @Test
    @DisplayName("独立态·有效 header (8~36 位) → 透传该值 入 MDC + 回响应头 + 结束清 MDC")
    void standalone_validHeader_transmitted() throws Exception {
        String clientTrace = "ws-trace-0a1b2c3d"; // 16 位合格
        HttpServletRequest req = reqWithHeader(clientTrace);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        final String[] during = new String[1];
        FilterChain chain = (r, w) -> during[0] = TraceContext.current();
        filter.doFilter(req, resp, chain);

        assertEquals(clientTrace, during[0], "handler 期间 MDC 应透传客户端 traceId");
        assertNull(TraceContext.current(), "独立态由本 filter 拥有 → 结束后必清");

        ArgumentCaptor<String> hdr = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(org.mockito.ArgumentMatchers.eq(TraceContext.HEADER_NAME), hdr.capture());
        assertEquals(clientTrace, hdr.getValue());
    }

    @Test
    @DisplayName("独立态·过短 header (<8) → 拒绝该值改生成 36 位 UUID")
    void standalone_shortHeader_rejected() throws Exception {
        HttpServletRequest req = reqWithHeader("12345"); // 5 位 < 8
        HttpServletResponse resp = mock(HttpServletResponse.class);
        final String[] during = new String[1];
        FilterChain chain = (r, w) -> during[0] = TraceContext.current();
        filter.doFilter(req, resp, chain);

        assertEquals(36, during[0].length(), "短 header 应被拒绝 → 生成 36 位 UUID");
        ArgumentCaptor<String> hdr = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(org.mockito.ArgumentMatchers.eq(TraceContext.HEADER_NAME), hdr.capture());
        assertEquals(36, hdr.getValue().length(), "响应头应为生成 UUID，不得携带过短 header");
    }

    @Test
    @DisplayName("独立态·过长 header (>36) → 拒绝该值改生成 36 位 UUID")
    void standalone_longHeader_rejected() throws Exception {
        HttpServletRequest req = reqWithHeader("a".repeat(37)); // 37 位 > 36
        HttpServletResponse resp = mock(HttpServletResponse.class);
        final String[] during = new String[1];
        FilterChain chain = (r, w) -> during[0] = TraceContext.current();
        filter.doFilter(req, resp, chain);

        assertEquals(36, during[0].length(), "过长 header 应被拒绝 → 生成 36 位 UUID");
        ArgumentCaptor<String> hdr = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(org.mockito.ArgumentMatchers.eq(TraceContext.HEADER_NAME), hdr.capture());
        assertEquals(36, hdr.getValue().length());
    }

    @Test
    @DisplayName("Monolith 态·外层 filter 已设 MDC → 本 filter 不覆盖不清 MDC，只回外层值到响应头")
    void monolith_alreadySet_doesNotOverrideAndDoesNotClear() throws Exception {
        String outerTrace = "gateway-set-trace-0001";
        TraceContext.put(outerTrace);   // 模拟 gateway RequestContextFilter 先行
        HttpServletRequest req = reqWithHeader("client-trace-should-be-ignored");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        final String[] during = new String[1];
        FilterChain chain = (r, w) -> during[0] = TraceContext.current();
        filter.doFilter(req, resp, chain);

        assertEquals(outerTrace, during[0],
                "外层 filter 已设值 → 本 filter 不得用客户端 header 覆盖");
        assertNotNull(TraceContext.current(),
                "monolith 态 MDC 由最外层 filter 的 finally 独占一次清，本 filter 不得提前清空；" +
                        "若此断言 FAIL = 本 filter 误当独立态清了 MDC（回归 = 双清或串号风险）");

        ArgumentCaptor<String> hdr = ArgumentCaptor.forClass(String.class);
        verify(resp).setHeader(org.mockito.ArgumentMatchers.eq(TraceContext.HEADER_NAME), hdr.capture());
        assertEquals(outerTrace, hdr.getValue(),
                "响应头应回外层已写入的值，不采用客户端 header");
        // 交由 @AfterEach 清理
        TraceContext.clear();
    }

    @Test
    @DisplayName("连续两请求 → 各自 traceId 独立，不串号")
    void twoSequentialRequests_noCrossContamination() throws Exception {
        final String[] first = new String[1];
        final String[] second = new String[1];
        HttpServletRequest req1 = reqWithHeader(null);
        HttpServletResponse resp1 = mock(HttpServletResponse.class);
        filter.doFilter(req1, resp1, (r, w) -> first[0] = TraceContext.current());

        HttpServletRequest req2 = reqWithHeader(null);
        HttpServletResponse resp2 = mock(HttpServletResponse.class);
        filter.doFilter(req2, resp2, (r, w) -> second[0] = TraceContext.current());

        assertNotNull(first[0]);
        assertNotNull(second[0]);
        assertNotEquals(first[0], second[0],
                "同一 filter 单例 (Servlet filter 是单例) 连续两请求应各自独立生成，不串号");
        assertNull(TraceContext.current(), "两次跑完 MDC 应已清空");
    }
}
