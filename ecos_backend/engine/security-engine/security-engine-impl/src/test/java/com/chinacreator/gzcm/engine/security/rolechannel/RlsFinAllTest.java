package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §2.2「角色 × 通道验收矩阵」第 1 行第 1 格 —— 财务负责人 / 页面查询
 * （可执行标识 {@code RlsFinAllTest#page}，#142/B4-3a）。
 *
 * <p><b>矩阵期望</b>：全量行 + {@code hourly_rate}/SALARY 可见。</p>
 *
 * <p><b>断言的真值来源</b>：策略集由 {@link V246SeedFixtures} 从
 * {@code V246__security_policy_seed.sql} 解析（单源，不在测试里抄策略），
 * 裁决由 {@link PageChannelHarness} 装配的<b>生产实现</b>
 * （RLS/CLS service + {@code SecurityDecisionService#decide}）算出，
 * OPA 源是真实现且因 {@code data.access} 策略不存在而 fail-close ——
 * 本用例只断行/列面，OPA 面见 {@code OpaFinAiTest}（B4-3c）。</p>
 *
 * <p><b>「全量行」的实现形态</b>：V246 给 finance_lead 的是
 * {@code source_kind=legacy_text} + {@code filter_expr='1=1'}，即<b>唯一</b>允许无条件放行的主体
 * （登记于 {@code ecos_security_asset.guard_combo}）。因此这里断的是"谓词不受限"，
 * 不是"返回了多少行"——行数据属业务域事实表，本批未授权写 dev PG，
 * 端到端行数验证等 seed 落库后补（差距文档剩余项）。</p>
 */
class RlsFinAllTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("page — finance_lead 在三张 fc_* 事实表上均得无限制谓词（1=1）且敏感列可见")
    void page() {
        List<String> tables = List.of(
                V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST, V246SeedFixtures.T_RESOURCE);

        for (String table : tables) {
            Map<String, Object> rls = harness.applyRls(table, V246SeedFixtures.FIN_USER);

            List<Map<String, Object>> hits = PageChannelHarness.policies(rls);
            assertFalse(hits.isEmpty(), "finance_lead 应在 " + table + " 命中策略，不得为空");
            assertTrue(hits.stream().allMatch(p -> "rls-finance-all".equals(String.valueOf(p.get("name")))),
                    "finance_lead 在 " + table + " 只应命中 rls-finance-all，实得 " + names(hits));

            String template = PageChannelHarness.predicateTemplate(rls);
            assertNotNull(template, table + " 必须有谓词模板");
            assertTrue(UnrestrictedPredicate.matches(template),
                    "矩阵第1行要求「全量行」= 无限制谓词，实得 template=" + template + " @ " + table);

            assertTrue(PageChannelHarness.bindingsOf(rls).isEmpty(),
                    "全量放行策略不应携带参数绑定（绑定只在受限谓词上出现）: " + table);
        }

        // 列面：hourly_rate / amount 对财务负责人可见（V246 的 CLS 黑名单只作用于 non_finance 角色）
        Map<String, Object> cls = harness.applyCls(V246SeedFixtures.T_COST,
                V246SeedFixtures.FIN_USER, V246SeedFixtures.COST_COLUMNS);
        List<String> visible = PageChannelHarness.visible(cls);
        assertTrue(visible.contains("hourly_rate"), "财务负责人应可见 hourly_rate，实得 " + visible);
        assertTrue(visible.contains("amount"), "财务负责人应可见 amount，实得 " + visible);
        assertTrue(PageChannelHarness.blocked(cls).isEmpty(),
                "财务负责人不应命中任何列屏蔽策略，实得 " + PageChannelHarness.blocked(cls));

        // 走唯一裁决入口再核一遍：页面通道不得因 OPA 缺策略被误算成"列全开"以外的结论
        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.FIN_USER);
        assertNotNull(b.decisionId(), "每通道必须有独立 decisionId");
        assertFalse(b.columnsDenied(), "finance_lead 的列投影不得为空");
        assertEquals(Boolean.FALSE, b.opa().get("allow"),
                "data.access rego 不存在 ⇒ OPA 面如实呈现 fail-close DENY（本批不伪造 ALLOW，见 B4-3c）");
    }

    @Test
    @DisplayName("page/缺陷 D-B4-6b — 财务可见 S3 列当前是「无 CLS 策略即全放行」的副产品，不是目录授权")
    void financeColumnVisibilityRidesClsFailOpen() {
        // 事实链：V246 只给 non_finance 播了 cls-non-finance-hide 两行，finance_lead 在 CLS 面 0 策略；
        // 而 getColumns 以「请求的全部列」起步再减 blocked ⇒ 0 策略 = 全列可见（fail-open）。
        // 矩阵第1行期望的是「S3 列对财务可见」由 ecos_security_asset(owner_role=finance_lead) 授权达成，
        // 现状则是黑名单默认放行——结论碰巧一致，成因相反，故必须钉为在册缺陷而非通过项。
        List<Map<String, Object>> finPolicies =
                harness.seed().selectClsPolicies(V246SeedFixtures.T_COST, V246SeedFixtures.FIN_USER);
        assertEquals(List.of(), finPolicies,
                "前提复核：seed 未给 finance_lead 配任何 CLS 策略（若日后补了 allow-list，本用例须改为授权断言）");

        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.FIN_USER);
        assertEquals(V246SeedFixtures.COST_COLUMNS, PageChannelHarness.visibleColumns(b),
                "D-B4-6b 现状钉桩：无策略 ⇒ 请求列全量放行；B4-4 收口（allow-list 默认拒绝）后此处应断目录授权列集");
    }

    @Test
    @DisplayName("page/交叉 — V246 里出现的每个 role_id 都在演示角色归属表里有主（防漏配致矩阵行失真）")
    void seedRolesAreAllCoveredByAssignment() {
        for (String roleId : harness.seed().seedRoleIds()) {
            boolean covered = List.of(V246SeedFixtures.FIN_USER, V246SeedFixtures.PROJ_USER,
                            V246SeedFixtures.DEPT_USER, V246SeedFixtures.NO_ROLE_USER).stream()
                    .anyMatch(u -> V246SeedFixtures.rolesFor(u).contains(roleId));
            assertTrue(covered, "seed 角色 " + roleId + " 无任何演示主体持有，矩阵断言会失真");
        }
    }

    private static List<String> names(List<Map<String, Object>> hits) {
        return hits.stream().map(p -> String.valueOf(p.get("name"))).toList();
    }

    /**
     * 「无限制谓词」判定：把合并产生的括号剥掉、按 AND 分段，每段必须恰为 {@code 1=1}；
     * 一旦出现列名／占位符即判为受限。刻意不用 {@code contains("1=1")} 之类宽松匹配——
     * 那样 "(department_id = :userDeptId) OR 1=1" 也会误判通过。
     */
    static final class UnrestrictedPredicate {
        static boolean matches(String template) {
            String noParens = template.replace("(", "").replace(")", "");
            String[] segments = noParens.split("(?i)\\s*AND\\s*");
            for (String segment : segments) {
                if (!"1=1".equals(segment.trim())) return false;
            }
            return segments.length > 0;
        }

        private UnrestrictedPredicate() { }
    }
}
