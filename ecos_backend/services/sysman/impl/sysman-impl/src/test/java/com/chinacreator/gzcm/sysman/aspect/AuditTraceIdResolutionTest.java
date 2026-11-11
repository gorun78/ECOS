package com.chinacreator.gzcm.sysman.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计 trace_id 取值优先级门禁（CLI 生产化批 C 的对账前提）。
 *
 * <p>计划 §三 的验收是「CLI 输出 {@code trace_id} == {@code ecos_audit_log.trace_id}」。
 * 这条等式成立的前提是审计侧真的把链路 trace 写进行里，且优先级正确：
 * MDC（gateway 注入）→ {@code X-Trace-Id} → {@code X-Request-Id} → null。
 * 顺序一旦颠倒（例如被调用方自带的 X-Request-Id 抢先），CLI 侧写入的 trace 就丢了。</p>
 */
class AuditTraceIdResolutionTest {

    @AfterEach
    void cleanUp() {
        MDC.remove("traceId");
        RequestContextHolder.resetRequestAttributes();
    }

    private static void givenRequestWithHeaders(String traceId, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (traceId != null) {
            request.addHeader("X-Trace-Id", traceId);
        }
        if (requestId != null) {
            request.addHeader("X-Request-Id", requestId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("MDC 优先于请求头（gateway 过滤器注入的是权威链路值）")
    void mdcWinsOverHeaders() {
        MDC.put("traceId", "cli-from-mdc");
        givenRequestWithHeaders("header-trace", "header-request");
        assertEquals("cli-from-mdc", AuditAspect.currentTraceId());
    }

    @Test
    @DisplayName("无 MDC：X-Trace-Id 优先于 X-Request-Id")
    void traceHeaderBeatsRequestIdHeader() {
        givenRequestWithHeaders("cli-x-trace", "legacy-request-id");
        assertEquals("cli-x-trace", AuditAspect.currentTraceId());
    }

    @Test
    @DisplayName("仅有旧头 X-Request-Id 时向后兼容回退")
    void fallsBackToLegacyRequestId() {
        givenRequestWithHeaders(null, "legacy-only");
        assertEquals("legacy-only", AuditAspect.currentTraceId());
    }

    @Test
    @DisplayName("都取不到 ⇒ null（调用方据此留 WARN，不伪造 trace）")
    void returnsNullWhenNothingAvailable() {
        assertNull(AuditAspect.currentTraceId());
    }

    @Test
    @DisplayName("MDC 空串不当作有效值，继续回退请求头")
    void blankMdcFallsThrough() {
        MDC.put("traceId", "   ");
        givenRequestWithHeaders("cli-x-trace", null);
        assertEquals("cli-x-trace", AuditAspect.currentTraceId());
    }
}
