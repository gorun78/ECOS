package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 详细设计-01 F01-08 / W40 — 机构（org）上下文只从服务端解析，客户端不可注入。
 *
 * <p>断言：</p>
 * <ul>
 *   <li>伪造 {@code X-Org-Id} 头 + {@code ?orgId=} 参数到达 gateway 后，
 *       下游（service 拦截器）视角对二者均为 null —— 即"伪造 X-Org-Id 不改变 orgId"；</li>
 *   <li>剥离原值落 {@code stripped.X-Org-Id} / {@code stripped.param.orgId} 观测属性（审计留痕）；</li>
 *   <li>不破坏既有的 X-ECOS-* 五头剥离语义（回归）。</li>
 * </ul>
 *
 * <p>服务侧（{@code ClearanceInterceptor#getCurrentOrgId}）已同步移除对客户端
 * X-Org-Id/orgId 的读取（org 只从 TD_USER 服务端解析），与本 gateway 剥离构成双重防线。</p>
 */
class ContextSourceTrustTest {

    private EcosTrustHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new EcosTrustHeaderFilter();
    }

    private HttpServletRequest runThroughFilter(MockHttpServletRequest req) throws Exception {
        AtomicReference<jakarta.servlet.ServletRequest> seen = new AtomicReference<>();
        FilterChain chain = (r, s) -> seen.set(r);
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) seen.get();
    }

    @Test
    @DisplayName("伪造 X-Org-Id 头 + ?orgId= 参数 — 下游视角均被剥离为 null（不可改变 orgId）")
    void forgedOrgIdHeaderAndParamAreStripped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/datanet/datasource?orgId=forged-org");
        req.setRequestURI("/api/v1/datanet/datasource");
        req.addHeader("X-Org-Id", "forged-org");
        req.addParameter("orgId", "forged-org");

        HttpServletRequest downstream = runThroughFilter(req);

        assertNull(downstream.getHeader("X-Org-Id"), "下游仍可见伪造 X-Org-Id（org 可被客户端劫持 → 越权面）");
        assertNull(downstream.getParameter("orgId"), "下游仍可见伪造 orgId 参数");
    }

    @Test
    @DisplayName("剥离原值保留在 stripped.* 观测属性（审计留痕）")
    void forgedOrgIdPreservedForAudit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/data");
        req.addHeader("X-Org-Id", "attacker-org");
        req.addParameter("orgId", "attacker-param");

        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> { });

        assertEquals("attacker-org", req.getAttribute("stripped.X-Org-Id"),
                "伪造 org 头必须留痕供审计");
        assertEquals("attacker-param", req.getAttribute("stripped.param.orgId"),
                "伪造 org 参数必须留痕供审计");
    }

    @Test
    @DisplayName("回归：既有 X-ECOS-* 五头剥离不受 W40 影响")
    void trustHeadersStillStripped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/data");
        req.addHeader("X-ECOS-USER", "forged-user");
        req.addHeader("X-ECOS-TENANT", "forged-tenant");

        HttpServletRequest downstream = runThroughFilter(req);

        assertNull(downstream.getHeader("X-ECOS-USER"));
        assertNull(downstream.getHeader("X-ECOS-TENANT"));
    }
}
