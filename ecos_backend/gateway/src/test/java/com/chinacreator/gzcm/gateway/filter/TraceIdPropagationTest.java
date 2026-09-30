package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.base.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-11 / W08 — traceId 全链贯穿门禁。
 *
 * <p>三处一致：
 * <ol>
 *   <li><b>请求 → MDC</b>：{@link RequestContextFilter} 沿用客户端 {@code X-Request-Id}，缺失则生成 32 位 traceId 并写入 MDC 键 {@code traceId}</li>
 *   <li><b>MDC → logback</b>：gateway {@code logback-spring.xml} 输出 pattern 含 {@code %X{traceId}}，把 MDC 落到日志</li>
 *   <li><b>MDC → 响应体</b>：{@link ApiResponse#withTraceIdFromMdc()} 从 MDC 读取回填 {@code traceId}，与响应头 {@code X-Request-Id} 同源</li>
 * </ol>
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='TraceIdPropagationTest'}</p>
 */
class TraceIdPropagationTest {

    private final FilterChain noopChain = new FilterChain() {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res) { }
    };

    @AfterEach
    void tearDown() {
        MDC.remove(RequestContextFilter.MDC_KEY_TRACE);
    }

    @Test
    @DisplayName("1. 客户端自带 X-Request-Id → 沿用（写入 MDC + 回响应头）")
    void inboundTraceIdIsReused() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "client-trace-123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        new RequestContextFilter().doFilter(req, resp, noopChain);

        assertEquals("client-trace-123", resp.getHeader("X-Request-Id"));
        // 过滤器链内 MDC 已设置；doFilter 的 finally 会清除，故在此断言响应头同源即可证明贯穿
    }

    @Test
    @DisplayName("2. 无 X-Request-Id → 生成 32 位 traceId 并回写响应头")
    void missingTraceIdIsGenerated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        new RequestContextFilter().doFilter(req, resp, noopChain);

        String tid = resp.getHeader("X-Request-Id");
        assertNotNull(tid, "缺失 traceId 时应生成");
        assertEquals(32, tid.length(), "生成的 traceId 为 32 位（UUID 去横线）");
    }

    @Test
    @DisplayName("3. 过滤器内 MDC 键 traceId 生效、doFilter 结束后清除（防线程池串号）")
    void mdcSetInsideAndClearedAfter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "mi-456");

        new RequestContextFilter().doFilter(req, new MockHttpServletResponse(),
                (r, s) -> {
                    // 链执行期间 MDC 必须已有 traceId（下游业务日志可破案）
                    assertEquals("mi-456", MDC.get("traceId"));
                });

        assertNull(MDC.get("traceId"), "doFilter 结束 finally 必须清除 MDC");
    }

    @Test
    @DisplayName("4. 响应体 traceId 与 MDC 同源（withTraceIdFromMdc 读 MDC 键 traceId）")
    void responseBodyTraceIdFromMdc() {
        MDC.put("traceId", "body-trace-789");
        ApiResponse<String> body = ApiResponse.success("ok").withTraceIdFromMdc();
        assertEquals("body-trace-789", body.getTraceId());
    }

    @Test
    @DisplayName("5. logback 输出 pattern 含 %X{traceId}（MDC → 日志贯穿）")
    void logbackPatternReadsMdcTraceId() throws Exception {
        String xml = readLogbackSpringXml();
        assertNotNull(xml, "未找到 gateway logback-spring.xml");
        assertTrue(xml.contains("%X{traceId"),
                "logback pattern 必须含 %X{traceId} 才能把 MDC 落到日志，实际: " + xml);
        assertFalse(xml.contains("%X{someOtherKey:-}") && !xml.contains("%X{traceId"),
                "pattern 需以 traceId 为键");
    }

    private static String readLogbackSpringXml() {
        try (InputStream in = TraceIdPropagationTest.class.getClassLoader()
                .getResourceAsStream("logback-spring.xml")) {
            if (in == null) {
                return null;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
