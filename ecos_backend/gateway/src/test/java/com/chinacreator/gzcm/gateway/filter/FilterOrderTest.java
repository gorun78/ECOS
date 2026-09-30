package com.chinacreator.gzcm.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 详细设计-00 C.1/W11 — 过滤器链规定序门禁。
 *
 * <p>断言对象是 5 个 gateway {@code @Component} Filter 的<b>真实 @Order</b>，
 * 改任何一个都会立刻红。规定序（C.1）：
 * <ol>
 *   <li>{@code HIGHEST+10} VersionPrefixRewrite（路径归一）</li>
 *   <li>{@code HIGHEST+11} RequestContext（traceId 入 MDC）</li>
 *   <li>{@code HIGHEST+20} EcosTrustHeader（剥离伪造信任头）</li>
 *   <li>{@code HIGHEST+30} SecurityHeaders（响应头）</li>
 *   <li>{@code 5} RateLimit（限流）</li>
 *   <li>{@code 6} Quota（配额）</li>
 * </ol>
 * 注意 Ordered：数值越小执行越早；HIGHEST_PRECEDENCE(Integer.MIN) 是"最早"，
 * 因此 10/11/20/30 全部早于 5/6（正数）执行。</p>
 */
class FilterOrderTest {

    @Test
    @DisplayName("version 前缀重写最先（+10）")
    void rewriteIsEarliest() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, order(VersionPrefixRewriteFilter.class));
    }

    @Test
    @DisplayName("RequestContext(+11) 紧跟重写、先于 trust(+20)")
    void requestContextAfterRewriteBeforeTrust() {
        int rc = order(RequestContextFilter.class);
        int trust = order(EcosTrustHeaderFilter.class);
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 11, rc);
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 20, trust);
        assertTrue(rc < trust, "RequestContext 数值必须小于 TrustHeader（更早执行）");
    }

    @Test
    @DisplayName("SecurityHeaders 在 trust 之后（+30），限流/配额在其之后（5/6）")
    void headersThenRateThenQuota() {
        int headers = order(SecurityHeadersFilter.class);
        int rate = order(RateLimitFilter.class);
        int quota = order(QuotaFilter.class);
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 30, headers);
        assertEquals(5, rate);
        assertEquals(6, quota);
        // trust(+20) < headers(+30) < 正数 limit/quota
        int trust = order(EcosTrustHeaderFilter.class);
        assertTrue(headers > trust, "SecurityHeaders 应在 trust 之后");
        assertTrue(rate > headers, "RateLimit 数值更大 ⇒ 更晚");
        assertTrue(quota > rate, "Quota 在 RateLimit 之后");
    }

    private void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private int order(Class<?> c) {
        Order o = AnnotationUtils.findAnnotation(c, Order.class);
        if (o == null) throw new AssertionError(c.getSimpleName() + " 缺少 @Order");
        return o.value();
    }
}
