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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §2.2 矩阵第 2 行第 1 格 —— 项目负责人 p1 / 页面查询
 * （可执行标识 {@code RlsProjOwnerTest#page}，#142/B4-3a）。
 *
 * <p><b>矩阵期望</b>：仅 p1 行；{@code hourly_rate} 列缺失。</p>
 *
 * <p><b>与设计文本的口径差（登记 D-B4-5，不改测试掩盖）</b>：V246 里 project_owner 的行级谓词是
 * {@code project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)}
 * —— 以「项目归属部门」表达"仅本人负责的项目"，<b>不是</b>设计文本字面的"仅 p1 行"（项目 id 直绑）。
 * 本用例断的是 seed 实际形态（参数化 + 部门归属子查询 + 非 1=1），并在断言消息里把这一差写明。
 * 若要按字面收口，需要 seed 增 {@code :userProjectId} 绑定或改投影，属策略面变更，另批裁决。</p>
 */
class RlsProjOwnerTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("page — project_owner 得参数化部门归属谓词（非 1=1），绑定源 CONTEXT_ORG")
    void page() {
        Map<String, Object> rls = harness.applyRls(V246SeedFixtures.T_PERIOD, V246SeedFixtures.PROJ_USER);

        List<Map<String, Object>> hits = PageChannelHarness.policies(rls);
        assertEquals(1, hits.size(), "p1 在 period 表应只命中 1 条策略，实得 " + hits);
        assertEquals("rls-project-owner", String.valueOf(hits.get(0).get("name")));
        assertEquals("parameterized", String.valueOf(hits.get(0).get("sourceKind")),
                "V246 的 project_owner 策略必须走参数化模板，不得退回裸文本 filter_expr");

        String template = PageChannelHarness.predicateTemplate(rls);
        assertTrue(template != null && !template.isBlank(), "RLS 谓词模板不得为空");
        // 生产合并逻辑给每段加括号，故断"含占位符 + 不是放行的 1=1"，不硬断字面量
        assertTrue(template.contains(":userDeptId"),
                "谓词必须保留 :userDeptId 占位符（值由服务端上下文绑定，禁字面量入 SQL），实得 " + template);
        assertFalse(RlsFinAllTest.UnrestrictedPredicate.matches(template),
                "project_owner 谓词必须是受限谓词，不得退化为 1=1 放行，实得 " + template);
        assertTrue(template.contains("biz_project_attribution"),
                "D-B4-5 口径差实证：seed 用「部门归属子查询」而非字面 project_id='p1'，实得 " + template);

        List<Map<String, Object>> bindings = PageChannelHarness.bindingsOf(rls);
        assertEquals(1, bindings.size(), "userDeptId 绑定必须恰好 1 条，实得 " + bindings);
        assertEquals("userDeptId", String.valueOf(bindings.get(0).get("name")));
        assertEquals("CONTEXT_ORG", String.valueOf(bindings.get(0).get("source")),
                "org 维度只能取服务端上下文（F01-08 禁客户端 org 头）");

        // 参数位（值待运行期上下文注入）必须出现在 params 键集里，执行侧才知道绑什么
        Map<String, Object> params = rlsParams(rls);
        assertTrue(params.containsKey("userDeptId"), "params 必须含 userDeptId 键，实得 " + params.keySet());
    }

    @Test
    @DisplayName("page/缺陷 D-B4-6a — 非财务角色的 CLS 现状是「整表全列拒绝」，不是矩阵要求的「只隐藏 2 列」")
    void hourlyRateColumnIsAbsent() {
        Map<String, Object> costCls = harness.applyCls(V246SeedFixtures.T_COST,
                V246SeedFixtures.PROJ_USER, V246SeedFixtures.COST_COLUMNS);
        List<String> visible = PageChannelHarness.visible(costCls);
        assertFalse(visible.contains("hourly_rate"), "矩阵第2行要求 hourly_rate 列缺失，实得 " + visible);
        assertFalse(visible.contains("amount"), "amount 同属 S3 敏感列须缺失，实得 " + visible);
        assertEquals(List.of("hourly_rate", "amount"), PageChannelHarness.blocked(costCls));

        // ↓↓↓ 特征化断言（钉现状，非认可现状）↓↓↓
        // V246 的 cls-non-finance-hide 行把 NOT NULL 列 visible_cols 播成占位 '[]'，
        // 而 ColumnLevelSecurityServiceImpl.getColumns 以 visibleSet.retainAll(parsed) 实现 allow 语义
        // ⇒ '[]' 解析成空 allow 集，把"仅屏蔽 2 列"放大成"整表 0 列可读"。
        // 收口项 B4-4 落地后本断言须改为 3 列投影断言（[project_id, department_id, period]）。
        assertEquals(List.of(), visible,
                "D-B4-6a 现状钉桩：visible_cols='[]' 占位 + retainAll ⇒ 全列拒绝；B4-4 后改为 3 列投影断言");

        Map<String, Object> resourceCls = harness.applyCls(V246SeedFixtures.T_RESOURCE,
                V246SeedFixtures.PROJ_USER, V246SeedFixtures.RESOURCE_COLUMNS);
        assertEquals(List.of(), PageChannelHarness.visible(resourceCls),
                "同一缺陷在 resource 表上亦成立（blocked 只含 hourly_rate，但 allow 空集覆盖全部列）");

        DecisionBundle b = harness.decidePage(V246SeedFixtures.T_COST,
                V246SeedFixtures.COST_COLUMNS, V246SeedFixtures.PROJ_USER);
        assertTrue(b.columnsDenied(),
                "承上：bundle 层 non_finance 主体现状是 columnsDenied=true（全列不可读），"
                        + "而非矩阵期望的「其余列可读」");
    }

    private static void assertNotNullish(String s) {
        assertTrue(s != null && !s.isBlank(), "RLS 谓词模板不得为空");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rlsParams(Map<String, Object> rlsResult) {
        Object p = rlsResult.get("params");
        return p instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
