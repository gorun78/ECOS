package com.chinacreator.gzcm.common.security.anonymous;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Category;
import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-01 F01-07 / W39 — 默认 DENY 匿名面回归门禁（PRD §2.2 case A/B 双断言）。
 *
 * <p>case A（应放行的族）：认证 / 健康 / 错误兜底三类匿名端点必须命中
 * {@link Category#ANONYMOUS}，同时其双路径（{@code /api/v1/X} 与 {@code /api/X}）都得放行——
 * 缺一即"匿名面漏登记"回归。</p>
 *
 * <p>case B（不应放行的族）：任意 engine 业务/数据端点必须<b>不</b>在匿名面、也<b>不</b>在
 * clearance 豁免面（均属核心业务数据面，任何"整族匿名/整族豁免"都是越权面）。</p>
 *
 * <p>该测试是 {@link AnonymousEndpointInventoryTest} 的回归镜像 — 前者守护"已列的路径形态合法"，
 * 后者守护"PRD §2.2 语义：该放行的放行、该拦的拦住"。二者一起构成 W39 的 <b>AnonymousEndpointRegistry
 * 单源</b>验证（拒绝 PRD yml {@code auth.whitelist.paths} 形态）。</p>
 */
class AnonymousRegressionTest {

    @Test
    @DisplayName("case A：认证族（login/refresh/logout/reset）必须匿名且双路径放行")
    void caseA_authFamilyIsAnonymousBothPaths() {
        // 双路径都要扁平匿名（VersionPrefixRewriteFilter 改写后两条都要可达）
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/auth/login"),
                "auth login v1 必须匿名（行走 JWT 前置状态）");
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/auth/login"),
                "auth login 裸路径必须匿名（双路径形态必须齐）");
        // 且属 ANONYMOUS 类（不是 ONLY CLEARANCE_EXEMPT）
        assertTrue(hasAnonymousEntry("/api/v1/auth/**"),
                "auth v1 族必须登记为 ANONYMOUS（不是仅 CLEARANCE_EXEMPT）");
    }

    @Test
    @DisplayName("case A：健康族（gateway/engine/knowledge health）必须匿名")
    void caseA_healthFamilyIsAnonymous() {
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/actuator/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/health"));
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/knowledge/health"));
        // 健康族必须 ANONYMOUS 类
        assertTrue(hasAnonymousEntry("/health") || hasAnonymousEntry("/health/**"),
                "/health 须登记为 ANONYMOUS");
    }

    @Test
    @DisplayName("case A：错误兜底 /error 必须匿名（否则 500 反过来阻断诊断）")
    void caseA_errorFallbackIsAnonymous() {
        assertTrue(AnonymousEndpointRegistry.isAnonymousOrExempt("/error"),
                "错误页必须匿名，否则未认证用户 500 会陷入无限读回环");
    }

    @Test
    @DisplayName("case B：engine 业务数据面必须 NOT 匿名也 NOT 豁免")
    void caseB_engineBusinessDataPlaneIsNotExempt() {
        // H9-T2b 举证：整族空白 = 越权面（PMO-B 判非收敛）
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/lineage"),
                "engine.data.lineage 是核心业务，必须走认证+准入");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/quality"),
                "engine.data.quality 必须走认证+准入");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/pipeline"),
                "engine.data.pipeline 必须走认证+准入");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/datanet/datasource"),
                "datanet 数据源 CRUD 必须走认证+准入");
    }

    @Test
    @DisplayName("case B：system/admin 元数据面必须 NOT 匿名（用户/角色/策略管理）")
    void caseB_systemAdminPlaneIsNotExempt() {
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/system/users"),
                "用户管理面必须拦截（L3 平面）");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/system/roles"),
                "角色管理面必须拦截");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/security/policies"),
                "安全策略管理面必须拦截（此路径属 CLEARANCE_EXEMPT 但仍需 JWT，不应进入 permitAll）");
    }

    @Test
    @DisplayName("case B：任何 CLS/RLS/mask/decision/crypt 端点必须 NOT 匿名（裁决面泄露 = 越权放大）")
    void caseB_securityDecisionEndpointsAreNotAnonymous() {
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/security/decision"),
                "decide 端点必须拦截（匿名即任意主体裁决）");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/security/rls/apply"),
                "RLS apply 端点必须拦截（匿名 = 任意用户 RLS 规则泄露）");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/security/assets"),
                "敏感资产目录端点必须拦截（匿名 = 敏感目录外泄）");
    }

    @Test
    @DisplayName("白名单不得越权包含 '*' 或宽通配 blanket '/api/**'（BSA-DENY 铁律）")
    void noBlanketWildcard() {
        Set<String> all = new TreeSet<>(AnonymousEndpointRegistry.allPatterns());
        assertFalse(all.contains("**") || all.contains("/api/**") || all.contains("/**")
                        || all.contains("/api/*") || all.contains("/"),
                "禁止任何形式的 blanket 通配（任意路径匿名即等于关认证）");
        // 逐条：仅拒收"整桶放行"的越权形态——路径直接以根/单层通配结尾且不带具体资源段。
        // 合法形态包含：两级族（/api/v1/x/**）、单段族（/auth/**）、子引擎通配（/api/v1/engine/*/health）、
        // 深层族（/api/v1/knowledge/extract/**）、以及 /health、/error 等单级具体路径。
        List<Entry> entries = AnonymousEndpointRegistry.all();
        for (Entry e : entries) {
            String p = e.getPattern();
            // 越权形态：命中 "=全部" 的通配，且没有任何具体资源段限定
            boolean rootOrFullWildcard = p.equals("/")
                    || p.equals("/**") || p.equals("**")
                    || p.equals("/api") || p.equals("/api/**") || p.equals("/api/*");
            assertFalse(rootOrFullWildcard,
                    "禁止 blanket 整桶放行（任意路径匿名即等于关认证）：" + p);
            // 每条豁免必须写清楚 justification
            String reason = e.getReason();
            assertTrue(reason != null && !reason.isBlank(),
                    "匿名/豁免每条必须写 justification（C.3.2 数据模型约束），pattern=" + p);
        }
    }

    @Test
    @DisplayName("禁止新增 permitAll 时不写 owner（C.3.2 强约束：owner/date 必填）")
    void entriesHaveOwner() {
        for (Entry e : AnonymousEndpointRegistry.all()) {
            String owner = e.getOwner();
            assertTrue(owner != null && !owner.isBlank(),
                    "每条豁免必须写 owner，pattern=" + e.getPattern());
            String date = e.getDate();
            assertTrue(date != null && date.matches("\\d{4}-\\d{2}-\\d{2}"),
                    "每条豁免必须写 date (yyyy-MM-dd)，pattern=" + e.getPattern());
        }
    }

    private boolean hasAnonymousEntry(String pattern) {
        for (Entry e : AnonymousEndpointRegistry.byCategory(Category.ANONYMOUS)) {
            if (e.getPattern().equals(pattern)) return true;
        }
        return false;
    }
}
