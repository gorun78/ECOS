package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 详细设计-07 F07-01-2 / C147 §8.1 点名 {@code workspacePrefixMappedInBothShapes} —
 * <b>「双路径各写一遍」铁律的 workspace 反例</b>（AGENTS.md 「其他约束」2 / 铁律 v2.0 §3.1）。
 *
 * <p>16 个场景域 Controller 全部直映射 {@code /api/v1/workspace/**}，因此：</p>
 * <ul>
 *   <li><b>反向</b> {@code /api/workspace/** → /api/v1/workspace/**} 必须存在于
 *       {@code REVERSE_PREFIX_MAP}（把无 v1 前缀的旧式调用导到正典）。</li>
 *   <li><b>正向</b> {@code /api/v1/workspace/** → /api/workspace/**} 必须<b>不</b>存在
 *       （同 knowledge-bases / datasource / pipeline 同规则：正向把正典改到空目标，网关 404）。</li>
 * </ul>
 *
 * <p>为什么用「应改写/不应改写」的路由行为断言而非字段反射：设计明明写的是 "workspacePrefixMap
 * ped<b>InBothShapes</b>"，两态映射的行为在中枢过滤器（本类）里就是"应/不应改写" —— 用行为级
 * 断言能同时证伪"反向被误删" 与 "正向被误加" 两种对偶的失配。字段反射只是这套行为的下位证据，
 * 不替代。</p>
 *
 * <p>断言面：不含 Spring 上下文（Controller 扫描依赖 gateway 完整启动 —— P-2 归属），只驱动
 * 过滤器本身 + {@link MockFilterChain} 观察 chain 收到的 servlet path。</p>
 */
class VersionPrefixRewriteFilterTest {

    private final VersionPrefixRewriteFilter filter = new VersionPrefixRewriteFilter();

    /** 直接调 {@code doFilter}（{@code OncePerRequestFilter} 通用入口），把 chain 收到
     *  的请求 uri 通过 mock 记录。 */
    private String servletPathAfterFilter(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServletPath(uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        final String[] seen = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest sr, jakarta.servlet.ServletResponse sresp) {
                seen[0] = ((HttpServletRequest) sr).getServletPath();
            }
        };
        filter.doFilter(req, res, chain);
        return seen[0];
    }

    @Test
    @DisplayName("F07-01-2 workspace 双路径：/api/workspace/** → /api/v1/workspace/**（反向命中，正典保留）")
    void workspacePrefixMappedInBothShapes() throws Exception {
        // 1) 反向：无 v1 前缀的 workspace 短前缀应被改写到 /api/v1/workspace/
        String shortPath = "/api/workspace/scenarios/sc-001/completeness";
        String rewritten = servletPathAfterFilter(shortPath);
        assertEquals("/api/v1/workspace/scenarios/sc-001/completeness", rewritten,
                "F07-01-2 / C147 §8.1 点名：/api/workspace/** 必须命中反向重写 → /api/v1/workspace/** "
                        + "（AGENTS.md 约束 2 '双路径各写一遍'）；实际 = " + rewritten);

        // 2) 正典：/api/v1/workspace/** 必须<b>不被</b>正向改写到 /api/workspace/
        //    （否则正典路径被改到空目标 → 网关 404 —— 同 knowledge-bases/datasource 同规则）
        String canonicalV1 = "/api/v1/workspace/scenarios";
        String canonical = servletPathAfterFilter(canonicalV1);
        assertEquals(canonicalV1, canonical,
                "F07-01-2 反例：/api/v1/workspace/** 必须原样通过（正典），不能被正向重写 "
                        + "（16 个场景 Controller 无 /api/workspace/ 副体，改到空目标即 404）");
        assertNotEquals("/api/workspace/scenarios", canonical,
                "正典路径 /api/v1/workspace/** 绝不得被正向改写到 mock 目标");
    }

    @Test
    @DisplayName("F07-01-2 反例：sibling 前缀 /api/workspacefoo/** 不得被误抓反向往 /api/v1/")
    void workspacePrefixMatchIsExactPrefixNotSubstringLeak() throws Exception {
        // requestURI 以 "/api/workspace" 前缀形式出现但非场景域 → 不应改写
        // （/api/workspace 后必须紧跟 / 才命中；"/api/workspacefoo" 前缀不匹配 "/api/workspace/"）
        String leaked = "/api/workspacefoo/scenarios/uncanonical";
        String result = servletPathAfterFilter(leaked);
        assertEquals(leaked, result,
                "反向前缀必须含 / 端点：/api/workspacefoo/** ≠ /api/workspace/** —— "
                        + "前缀串必须带尾部斜杠防邻居宽化");
    }

    @Test
    @DisplayName("对照：既有 agents/dq 反向映射仍工作（新加 workspace 未把已有条目挤飞 Map.ofEntries 容量）")
    void existingReverseEntriesStillResolved() throws Exception {
        assertEquals("/api/v1/dq/cases", servletPathAfterFilter("/api/dq/cases"),
                "PMO-48-A T2 反向 /api/dq/ 尚未失配");
        assertEquals("/api/v1/agents/agents", servletPathAfterFilter("/api/agents/agents"),
                "反向 prefix /api/agents/ 尚未失配");
        assertEquals("/api/absolute-nonleaky-e2e-test",
                servletPathAfterFilter("/api/absolute-nonleaky-e2e-test"),
                "未登记路径不得被无意改写（Map.ofEntries 加 workspace 条目后新 key 不影响既有匹配）");
    }
}
