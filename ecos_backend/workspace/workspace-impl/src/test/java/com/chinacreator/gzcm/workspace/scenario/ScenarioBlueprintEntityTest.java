package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蓝图实体「行形态热列」回环回归（补充设计 §6.2/§6.3/§6.6；2026-10-11 live 契约核验发现的
 * no-op setter 静默丢写缺陷的固化防线）。
 *
 * <p>缺陷形态：Service/DAO 读写面全部走 *_text / contentJson / actor / transitionReason
 * 文本访问器，而实体曾把这些 setter 写成 no-op、getter 恒 null ⇒ 创建/修订行的热列
 * 全部落 NULL（live 实测 gaps_text='[]' 常量都丢）。本测试锁定两侧口径：</p>
 * <ul>
 *   <li>raw 热列 setter→getter 原样回环（写路径 insert 前取值即 raw）；</li>
 *   <li>raw 为 null 时回落结构化序列化（逻辑模型投影不被破坏）；</li>
 *   <li>raw 优先于结构化（读路径 rowmapper 回填后 VO 装配不被二次序列化污染）。</li>
 * </ul>
 */
class ScenarioBlueprintEntityTest {

    @Test
    @DisplayName("热列 raw 回环：goalSuccessText/scope/deps/caps/gaps/risks/tests/content/actor/reason 写后即读")
    void rawTextColumnsRoundTrip() {
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setGoalSuccessText("[\"s1\"]");
        bp.setScopeJsonText("{\"tenantId\":\"t1\"}");
        bp.setDependenciesText("[{\"id\":\"d1\"}]");
        bp.setCapabilitiesText("[{\"capabilityId\":\"c1\"}]");
        bp.setGapsText("[]");
        bp.setRisksText("[{\"riskId\":\"r1\"}]");
        bp.setTestsText("[{\"testId\":\"p0-1\"}]");
        bp.setContentJson("{\"version\":1}");
        bp.setActor("u-admin");
        bp.setTransitionReason("draft created");

        assertEquals("[\"s1\"]", bp.getGoalSuccessText());
        assertEquals("{\"tenantId\":\"t1\"}", bp.getScopeJsonText());
        assertEquals("[{\"id\":\"d1\"}]", bp.getDependenciesText());
        assertEquals("[{\"capabilityId\":\"c1\"}]", bp.getCapabilitiesText());
        assertEquals("[]", bp.getGapsText());
        assertEquals("[{\"riskId\":\"r1\"}]", bp.getRisksText());
        assertEquals("[{\"testId\":\"p0-1\"}]", bp.getTestsText());
        assertEquals("{\"version\":1}", bp.getContentJson());
        assertEquals("u-admin", bp.getActor());
        assertEquals("draft created", bp.getTransitionReason());
    }

    @Test
    @DisplayName("结构化投影回落：raw 为 null 时 *_text 由嵌套模型序列化")
    void structuredFallbackStillWorks() {
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setGaps(List.of(Map.of("gapId", "g1", "category", "DATA")));
        bp.setGoalSuccessText(null);

        String gapsJson = bp.getGapsText();
        assertTrue(gapsJson != null && gapsJson.contains("g1"), "gaps 结构化投影应仍可序列化: " + gapsJson);

        ScenarioBlueprint.Goal goal = new ScenarioBlueprint.Goal();
        goal.setStatement("st");
        goal.setSuccessCriteria(List.of("ok"));
        bp.setGoal(goal);
        assertTrue(bp.getGoalSuccessText().contains("ok"));
    }

    @Test
    @DisplayName("raw 优先于结构化：rowmapper 回填 raw 后 getter 不被投影覆盖")
    void rawWinsOverStructured() {
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setGaps(List.of(Map.of("gapId", "g1")));
        bp.setGapsText("[]"); // raw（例如 DAO 读回的 '[]'）优先
        assertEquals("[]", bp.getGapsText());
    }

    @Test
    @DisplayName("approval_ref 落 Approval.approvalRef（不得串写 policyRef）；releasedTime 以 LocalDateTime 回环")
    void approvalRefAndReleasedTimeSemantics() {
        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setApprovalRef("sec-decision-001");
        assertEquals("sec-decision-001", bp.getApprovalRef());
        assertNull(bp.getApproval().getPolicyRef(), "approval_ref 列不得污染 policyRef 字段");

        LocalDateTime t = LocalDateTime.of(2026, 10, 11, 14, 30, 0);
        bp.setReleasedTime(t);
        assertEquals(t, bp.getReleasedTime());
    }
}
