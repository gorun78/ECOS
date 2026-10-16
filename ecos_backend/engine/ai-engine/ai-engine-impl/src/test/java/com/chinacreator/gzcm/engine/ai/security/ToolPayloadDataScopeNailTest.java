package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 工具裁决载荷的 {@code dataScope} 形态钉（#142/B4-3d-3，登记差距 <b>D-B4-29</b>）。
 *
 * <p><b>本类是「钉现状（非认可）」用例</b>：它断的不是"应该如此"，而是"现在如此，
 * 且这是 §4.7.2 AI 列在生产形态下的可达性缺口"。改代码时若把该段补齐，本用例会红，
 * 逼着改动者同时更新差距文档 —— 禁把它当"已通过"的验收证据引用。</p>
 *
 * <p><b>缺口的准确形态</b>：{@link ToolAdjudicationRequest.Resource} 没有
 * {@code departmentId} 字段，工具入参白名单也不含它，因此生产载荷的
 * {@code resource.dataScope} 只声明 {@code projectIds}（与策略不读的 {@code periods}）。
 * 策略侧 {@code requested_department} 于是取默认 {@code ""}，与已解析的
 * {@code subject_department}（非空）不等 ⇒ 落
 * {@code AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT}：明明是本域请求，却被按横向越权拒。
 * {@code data_access_oracles.json} 里 {@code proj_ai_p1} 的 ALLOW 只有<b>手工声明域</b>
 * 才可达（本批已由 {@code OpaProjOwnerTest} 经 {@code /decision} 唯一入口证明该路径成立），
 * 而 Agent 工具那条路<b>还不产生声明域</b>。</p>
 *
 * <p><b>为什么本批不顺手补</b>：补 {@code departmentId} 需要先裁决"声明域从哪来"
 * （场景上下文 or 服务端主体所属域 or 调用方显式请求），三选一都是策略语义输入；
 * 未裁决就写进生产载荷，等于用一条自造链路冒充 §4.7.2 收口。</p>
 */
class ToolPayloadDataScopeNailTest {

    private static ToolAdjudicationRequest requestWith(Map<String, Object> args) {
        return new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject("u-pj", List.of("project_owner"), "d01", "t-demo"),
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE,
                        "sql_query_tool", "query"),
                ToolAdjudicationRequest.projectResource(args),
                new ToolAdjudicationRequest.Environment("trace-nail",
                        ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-16T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scopeOf(ToolAdjudicationRequest req) {
        Map<String, Object> resource = (Map<String, Object>) req.toEvaluateInput()
                .get(DataScopeContract.KEY_RESOURCE);
        return (Map<String, Object>) resource.get(DataScopeContract.KEY_DATA_SCOPE);
    }

    @Test
    @DisplayName("钉现状 — 生产工具载荷声明 projectIds 但从不声明 departmentId（D-B4-29）")
    void productionToolPayloadNeverDeclaresDepartment() {
        Map<String, Object> scope = scopeOf(requestWith(Map.of(
                "projectIds", List.of("p1"), "periods", List.of("2026-10"))));

        assertEquals(List.of("p1"), scope.get(DataScopeContract.KEY_PROJECT_IDS));
        assertFalse(scope.containsKey(DataScopeContract.KEY_DEPARTMENT_ID),
                "D-B4-29：Resource 无 departmentId 字段 ⇒ 策略侧 requested_department 取默认空串，"
                        + "域内请求也会被标越权。补齐该字段前本断言必须保持为「不含」");
        assertTrue(ToolAdjudicationRequest.resourceArgWhitelist().stream()
                        .noneMatch(DataScopeContract.KEY_DEPARTMENT_ID::equals),
                "白名单若已含 departmentId 说明裁决已落地，须同步撤销本钉并更新差距文档");
    }

    @Test
    @DisplayName("越权面 — 调用方在工具入参里自带 departmentId 也不得进入裁决载荷")
    void callerSuppliedDepartmentIsDropped() {
        Map<String, Object> scope = scopeOf(requestWith(Map.of(
                "projectIds", List.of("p1"),
                "departmentId", "d99",
                "scenarioId", "sc001")));

        assertFalse(scope.containsKey(DataScopeContract.KEY_DEPARTMENT_ID),
                "LLM/调用方自报的域标识不得成为策略输入（信任边界见 DataScopeContract 类注释）");
        assertEquals(List.of("p1"), scope.get(DataScopeContract.KEY_PROJECT_IDS));
    }

    @Test
    @DisplayName("单源委托 — 键面取自 DataScopeContract，策略侧可读同一形态")
    void payloadKeysComeFromSingleSource() {
        Map<String, Object> scope = scopeOf(requestWith(Map.of("projectIds", List.of("p1"))));
        // 载荷段与 /decision 写出段同键面 ⇒ 策略只需一套读取逻辑（D-B4-20 的第二套字面量已消）
        assertEquals(scope.keySet(),
                java.util.Set.of(DataScopeContract.KEY_PROJECT_IDS));
    }

    @Test
    @DisplayName("未声明项目集 — dataScope 段整体不写（禁空段冒充声明）")
    void emptyScopeSegmentIsOmitted() {
        Map<String, Object> input = requestWith(Map.of("scenarioId", "sc001")).toEvaluateInput();
        Object resource = input.get(DataScopeContract.KEY_RESOURCE);
        assertTrue(resource instanceof Map<?, ?> m && !m.containsKey(DataScopeContract.KEY_DATA_SCOPE),
                "无 projectIds/periods 时不得出现空的 dataScope 段: " + resource);
    }
}
