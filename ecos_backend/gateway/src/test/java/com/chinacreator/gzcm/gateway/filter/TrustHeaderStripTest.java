package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-03 / W02 — gateway 信任头剥离子门禁。
 *
 * <p>断言 {@link EcosTrustHeaderFilter} 的<b>剥离</b>阶段真正把
 * {@code X-ECOS-USER/ROLE/TENANT/DOMAIN/SERVICE} 五个可伪造头从下游可见视角清除
 * （伪造头被剥离即 fail），并把原值落到 {@code stripped.X-ECOS-*} 观测属性。
 * 重注入阶段依赖 SecurityContext（本 M0 无 Spring context），仅验证匿名 health 路径不写注入属性。
 *
 * <p>验收：{@code mvn -pl gateway -am test -Dtest='TrustHeaderStripTest'}</p>
 */
class TrustHeaderStripTest {

    private static final String[] TRUST_HEADERS = {
            "X-ECOS-USER", "X-ECOS-ROLE", "X-ECOS-TENANT", "X-ECOS-DOMAIN", "X-ECOS-SERVICE"
    };

    @Test
    @DisplayName("伪造的 5 个 X-ECOS-* 头在下游视角全部被剥离为 null")
    void forgedTrustHeadersAreStripped() throws Exception {
        EcosTrustHeaderFilter filter = new EcosTrustHeaderFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/data");
        req.setMethod("GET");
        for (String h : TRUST_HEADERS) {
            req.addHeader(h, "forged-" + h);
        }
        req.addHeader("Authorization", "Bearer t");

        AtomicReference<jakarta.servlet.ServletRequest> seenByChain = new AtomicReference<>();
        FilterChain chain = (r, s) -> seenByChain.set(r);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        jakarta.servlet.ServletRequest downstream = seenByChain.get();
        assertTrue(downstream instanceof jakarta.servlet.http.HttpServletRequest,
                "过滤器应把 mock req 包成 HttpServletRequest 传给下游");
        jakarta.servlet.http.HttpServletRequest httpDownstream =
                (jakarta.servlet.http.HttpServletRequest) downstream;
        for (String h : TRUST_HEADERS) {
            assertNull(httpDownstream.getHeader(h), "下游仍可见伪造头 " + h + "（剥离失效）");
        }
    }

    @Test
    @DisplayName("剥离原值保留在 stripped.* 观测属性（供日志/审计）")
    void strippedValuesKeptAsAttribute() throws Exception {
        EcosTrustHeaderFilter filter = new EcosTrustHeaderFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/data");
        req.addHeader("X-ECOS-USER", "attacker");

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> { });

        assertEquals("attacker", req.getAttribute("stripped.X-ECOS-USER"));
    }

    @Test
    @DisplayName("匿名/health 路径不写注入属性（应跳过重注入）")
    void healthPathDoesNotInject() throws Exception {
        EcosTrustHeaderFilter filter = new EcosTrustHeaderFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/health");

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> { });

        assertNull(req.getAttribute(EcosTrustHeaderFilter.ATTR_INJECTED),
                "health 路径无 authentication，不应写注入属性");
    }

    @Test
    @DisplayName("strip/remove 语义对称：剥离的头集合与设计 C.2 的五头一致")
    void trustHeaderSetMatchesDesign() {
        // 防止有人往 TRUST_HEADERS 里漏头（绕过 fake 检测）
        java.util.List<String> expected = Arrays.asList(
                "X-ECOS-USER", "X-ECOS-ROLE", "X-ECOS-TENANT", "X-ECOS-DOMAIN", "X-ECOS-SERVICE");
        assertEquals(expected, Arrays.asList(TRUST_HEADERS));
    }
}
