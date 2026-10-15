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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §2.2 矩阵第 3 行第 1 格 —— 部门用户 d01 / 页面查询
 * （可执行标识 {@code RlsDeptTest#page}，#142/B4-3a）。
 *
 * <p><b>矩阵期望</b>：仅 d01 本部门维度行。</p>
 *
 * <p>与 project_owner 行的区别在于谓词形态：dept_user 是<b>直接维度列</b>比较
 * （{@code department_id = :userDeptId}），而非归属子查询。本用例把该字面形态钉住，
 * 使"两条不同谓词被合并成同一条"或"维度列被替换成 project_id"这类改动能变红。</p>
 */
class RlsDeptTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("page — dept_user 在三张表上均得 (department_id = :userDeptId) 单一维度谓词")
    void page() {
        List<String> tables = List.of(
                V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST, V246SeedFixtures.T_RESOURCE);

        for (String table : tables) {
            Map<String, Object> rls = harness.applyRls(table, V246SeedFixtures.DEPT_USER);

            List<Map<String, Object>> hits = PageChannelHarness.policies(rls);
            assertEquals(1, hits.size(), table + " 上 d01 应只命中 1 条策略，实得 " + hits);
            assertEquals("rls-dept-scope", String.valueOf(hits.get(0).get("name")));
            assertEquals("90", String.valueOf(hits.get(0).get("priority")),
                    "rls-dept-scope 的 priority 必须保持 seed 值 90（priority 决定合并顺序）");

            String template = PageChannelHarness.predicateTemplate(rls);
            assertEquals("(department_id = :userDeptId)", template,
                    "d01 的谓词必须是部门维度列比较（含 mergePolicies 的段括号），实得 " + template);

            List<Map<String, Object>> bindings = PageChannelHarness.bindingsOf(rls);
            assertEquals(1, bindings.size());
            assertEquals("userDeptId", String.valueOf(bindings.get(0).get("name")));
            assertEquals("CONTEXT_ORG", String.valueOf(bindings.get(0).get("source")),
                    "部门维度只从服务端上下文取（F01-08）");

            assertTrue(PageChannelHarness.denyIfEmpty(rls),
                    "谓词必须带 denyIfEmpty=true，供执行侧在绑定缺失时拒绝而非放行: " + table);
        }
    }

    @Test
    @DisplayName("page/缺陷 D-B4-6a — d01 的 CLS 现状：blocked 命中 2 列，但 visible 被 '[]' 占位放大成 0 列")
    void nonFinanceColumnsAreHidden() {
        Map<String, Object> cls = harness.applyCls(V246SeedFixtures.T_COST,
                V246SeedFixtures.DEPT_USER, V246SeedFixtures.COST_COLUMNS);
        assertEquals(List.of("hourly_rate", "amount"), PageChannelHarness.blocked(cls));
        // 特征化断言（钉现状）：矩阵期望 [project_id, department_id, period]，
        // 实得空集 = seed 的 visible_cols='[]' 占位在 allow 语义下即"全列拒绝"（收口项 B4-4）。
        assertEquals(List.of(), PageChannelHarness.visible(cls),
                "D-B4-6a 现状钉桩：B4-4 收口后本断言须改为 3 列投影断言");

        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.DEPT_USER);
        assertEquals("(department_id = :userDeptId)", b.rls().get("predicateTemplate"),
                "bundle.rls 必须是部门维度谓词");
        assertTrue(b.columnsDenied(), "CLS 现状下 d01 全列不可读（缺陷放大面），收口后应为 false");
    }
}
