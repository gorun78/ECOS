package com.chinacreator.gzcm.engine.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog;
import com.chinacreator.gzcm.engine.ai.copilot.CopilotToolCatalog.Tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-COP-034 提示注入防护：被注入的 LLM 即使把「越权意图」写进工具入参，也不得穿透到出向载荷。
 *
 * <p>本类只钉<b>结构性防护</b>（服务端强制收口），不依赖 OPA 策略是否配了对应规则 ——
 * 后者当前是登记在案的配置缺口（rbac.rego 无 {@code ai:guardrail:*} 放行规则），
 * 因此在那边修好之前，「注入文本被模型转成越权入参」这条路径必须由结构闸独立挡住。</p>
 *
 * <p>三道断言各自可反证：</p>
 * <ol>
 *   <li>写工具终态由服务端改写为 DRAFT（模型说 APPROVED/PUBLISHED 均无效）；</li>
 *   <li>身份字段（approver/userId/tenantId/createdBy/operator/approvedBy）从载荷剔除；</li>
 *   <li>T4 高危动作（审批/发布/删除/密钥）在工具面不存在，注入文本无法命名到一个可执行的工具。</li>
 * </ol>
 */
@DisplayName("V-COP-034 提示注入防护：出向载荷的结构性收口")
class PromptInjectionPayloadConfinementTest {

    /** 典型提示注入语料：诱导模型把终态与身份写进工具入参。 */
    private static final List<String> TERMINAL_STATE_INJECTIONS = List.of(
            "APPROVED", "PUBLISHED", "RELEASED", "EFFECTIVE", "DONE");

    private static ScenarioToolDefinition createDraft() {
        return ScenarioToolCatalog.all().stream()
                .filter(d -> ScenarioToolCatalog.ACTION_CREATE_DRAFT.equals(d.toolKey()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("注入终态无效：模型给的所有终态值都被服务端改写为 DRAFT")
    void injectedTerminalStatesAreOverwrittenWithDraft() {
        ScenarioToolDefinition def = createDraft();
        for (String injected : TERMINAL_STATE_INJECTIONS) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("scenarioId", "s-1");
            args.put("status", injected);

            Map<String, Object> payload = ScenarioToolCatalog.enforceWritePayload(def, args);

            assertEquals(ScenarioToolCatalog.DRAFT_STATE, payload.get("status"),
                    "注入终态 " + injected + " 穿透到出向载荷: " + payload);
        }
    }

    @Test
    @DisplayName("自报身份无效：六类身份字段全部从载荷剔除，非身份字段原样透传")
    void selfReportedIdentityFieldsAreStripped() {
        ScenarioToolDefinition def = createDraft();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("scenarioId", "s-1");
        args.put("title", "注入标题原样保留");
        for (String field : ScenarioToolCatalog.IDENTITY_FIELDS) {
            args.put(field, "attacker-" + field);
        }

        Map<String, Object> payload = ScenarioToolCatalog.enforceWritePayload(def, args);

        for (String field : ScenarioToolCatalog.IDENTITY_FIELDS) {
            assertFalse(payload.containsKey(field),
                    "身份字段 " + field + " 未被剔除（审批人必须来自 token 主体）: " + payload);
        }
        assertEquals("s-1", payload.get("scenarioId"), "非身份字段被误删");
        assertEquals("注入标题原样保留", payload.get("title"), "非身份字段被误删");
    }

    @Test
    @DisplayName("正向对照：读工具载荷原样透传（证明上两条不是「一律清空」的假绿）")
    void readToolPayloadIsPassedThroughUnchanged() {
        ScenarioToolDefinition read = ScenarioToolCatalog.all().stream()
                .filter(d -> !d.isWrite())
                .findFirst().orElseThrow();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("runId", "r-1");
        args.put("status", "APPROVED");

        Map<String, Object> payload = ScenarioToolCatalog.enforceWritePayload(read, args);

        assertEquals("APPROVED", payload.get("status"),
                "读工具不应被改写载荷（改写只针对写工具的终态）");
        assertEquals("r-1", payload.get("runId"));
    }

    @Test
    @DisplayName("注入无法命名高危动作：T4 面零注册，工具名中不含审批/发布/删除/密钥动词")
    void injectedHighRiskActionsHaveNoToolToName() {
        List<String> banned = List.of(
                "approve", "reject", "publish", "rollback", "deprecate", "delete",
                "rotate", "impersonate", "kill");

        for (Tool tool : CopilotToolCatalog.all()) {
            String lower = tool.name().toLowerCase();
            for (String verb : banned) {
                assertFalse(lower.contains(verb),
                        "高危动作进入了工具面，注入文本可直接点名执行: " + tool.name());
            }
            assertTrue("T1".equals(tool.tier()) || "T2".equals(tool.tier()) || "T3".equals(tool.tier()),
                    "工具层级越界（T4 不应出现在工具面）: " + tool.name() + " tier=" + tool.tier());
        }
    }

    @Test
    @DisplayName("受控执行仍需人工确认：T3 工具不带 confirm=true 时不发起任何外呼")
    void tier3StillRequiresExplicitConfirmation() {
        List<Tool> tier3 = CopilotToolCatalog.all().stream()
                .filter(t -> "T3".equals(t.tier())).toList();
        assertFalse(tier3.isEmpty(), "工具面应存在受控执行类工具（否则本断言无判别力）");

        com.chinacreator.gzcm.engine.ai.service.ToolRegistry registry =
                new com.chinacreator.gzcm.engine.ai.service.ToolRegistry(null);
        com.chinacreator.gzcm.engine.ai.copilot.CopilotToolRegistrar registrar =
                new com.chinacreator.gzcm.engine.ai.copilot.CopilotToolRegistrar();
        org.springframework.test.util.ReflectionTestUtils.setField(registrar, "toolRegistry", registry);
        org.springframework.test.util.ReflectionTestUtils.setField(registrar, "support",
                new com.chinacreator.gzcm.engine.ai.copilot.CopilotToolSupport(null));
        registrar.register();

        for (Tool tool : tier3) {
            var result = registry.execute(tool.name(), Map.of("confirm", false));
            assertFalse(result.isSuccess(), tool.name() + " 显式 confirm=false 仍被执行");
            assertTrue(result.getError() != null && result.getError().contains("confirm"),
                    tool.name() + " 拒绝信息须指向 confirm，实际: " + result.getError());
        }
    }
}
