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
 * AI 工具裁决载荷的 {@code dataScope} 形态钉（#142/B4-3d-3 建立，
 * #142/B4-3d-11 依 <b>D-B4-29</b> 的取证把定性由「缺口」翻正为「授权边界」）。
 *
 * <p><b>本类断的是「必须如此」</b>：{@link ToolAdjudicationRequest.Resource} 没有
 * {@code departmentId} 字段、工具入参白名单也不含它 —— 这不是没做完，而是<b>不许这么做</b>。
 * 判据来自执行腿：SQL 类工具走 {@code ToolExecutorService#executeSql} → {@code queryForList}，
 * 只有只读地板 + 表白名单 + CLS 列面，<b>没有任何 RLS 行级下推</b>。因此策略里的
 * 「声明域 vs 主体域」比对是这条通道上唯一的行范围闸；一旦让调用方（LLM 可控）自报
 * {@code departmentId}，等于让被裁决者申报裁决依据 ⇒ 一行入参即可越域。
 * {@link #callerSuppliedDepartmentIsDropped()} 就是这条边界的可变异证明：
 * 把该键加进白名单，它立刻变红。</p>
 *
 * <p><b>被本批否掉的两条"顺手修法"</b>（登记以免后人重走）：
 * ① 给白名单加 {@code departmentId} ⇒ 上述安全回退；
 * ② 用 {@code subject.department} 回填声明域 ⇒ 比对两侧同源，恒等于放行，
 *    横向越权地板（矩阵 p2 DENY）当场失效。</p>
 *
 * <p><b>真实后果与排期</b>：§4.7.2 AI 列 p1「域内 ALLOW」在生产工具形态下不可达。
 * 关掉它只能靠<b>服务端真值解析</b>出声明域（D-B4-36，前置 D-B4-30 把裁决腿并入
 * {@code /decision}）。本批先把误标修成诚实码
 * {@code AI_DEPARTMENT_NOT_DECLARED}（旧落 {@code AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT}，
 * 把"载荷从未声明域"说成"这人跨域"），由 oracle 例
 * {@code ai_tool_no_department_declared} 在 {@code AiGuardrailVerdictFromOracleTest} 上证明。</p>
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
    @DisplayName("授权边界 — 声明域不得由调用方经工具入参自报（SQL 执行腿无 RLS 下推，本钉承重）")
    void productionToolPayloadNeverDeclaresDepartment() {
        Map<String, Object> scope = scopeOf(requestWith(Map.of(
                "projectIds", List.of("p1"), "periods", List.of("2026-10"))));

        assertEquals(List.of("p1"), scope.get(DataScopeContract.KEY_PROJECT_IDS));
        assertFalse(scope.containsKey(DataScopeContract.KEY_DEPARTMENT_ID),
                "白名单一旦放进 departmentId，被裁决者就能自行申报行范围依据 ⇒ 本通道无 RLS 下推，"
                        + "策略比对是唯一闸（差距 D-B4-36 只允许服务端真值解析来关，不许来自报）");
        assertTrue(ToolAdjudicationRequest.resourceArgWhitelist().stream()
                        .noneMatch(DataScopeContract.KEY_DEPARTMENT_ID::equals),
                "白名单含 departmentId ⇒ 安全回退（把 §4.7.2 的域判定交给 LLM 入参），非收口");
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
