package com.chinacreator.gzcm.engine.ai.wagent.arch;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 术语/命名单源守卫（详细设计-10 F10-01；验收名 {@code wDomainNeverAliasedToAgent}）。
 *
 * <p>F10-01 红线：W 编排域在 <b>API 前缀、包名、表名、事件名</b>四个面上都不得被别名为
 * 既有的 "Agent" 族（{@code /api/v1/agent} 混乱面、{@code ecos_ai.agent_*} 存量表、
 * {@code agt_} 附件前缀）。本域正典 = {@code /api/v1/wagent} +
 * {@code com.chinacreator.gzcm.engine.ai.wagent} + {@code ecos_ai.ecos_wagent_*}（R-33①）。</p>
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与实现包属同义（分册 §2 口径注）。</p>
 */
class TerminologyArchTest {

    /** "agent" 别名的可观察形态（路径段级，禁误伤 wagent 本身）。 */
    private static final List<String> ALIAS_SEGMENTS = List.of("/agent", "/agent/", "/v1/agent", "/v1/agent/");

    @Test
    @DisplayName("F10-01：wagent 路径常量只挂 /api/v1/wagent（+双路径 legacy 登记），绝不被别名为 /api[/v1]/agent")
    void wDomainNeverAliasedToAgent() throws Exception {
        List<String> pathConstants = new ArrayList<>();
        for (Field field : WAgentApiPaths.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != String.class) {
                continue;
            }
            String value = (String) field.get(null);
            if (value == null) {
                continue;
            }
            if (field.getName().startsWith("P_")) {
                assertFalse(value.startsWith("/"), "路径参数名不得含斜杠: " + field.getName());
                continue;
            }
            pathConstants.add(field.getName() + "=" + value);

            // ① 前缀单源：除 BASE/LEGACY 两个前缀登记常量外，一切端点常量必须挂 v1 正典前缀。
            boolean isLegacyRegistration = WAgentApiPaths.LEGACY_PREFIX.equals(value);
            assertTrue(value.startsWith(WAgentApiPaths.BASE_PREFIX) || isLegacyRegistration,
                    "端点常量必须挂 " + WAgentApiPaths.BASE_PREFIX + "（legacy 仅供反向重写登记）: "
                            + field.getName() + "=" + value);

            // ② 别名禁令：路径中不得出现独立的 agent 段（wagent 不匹配，因段边界为 "agent" 前缀不同）。
            for (String alias : ALIAS_SEGMENTS) {
                assertFalse(value.equals(alias) || value.contains(alias),
                        "W 域路径被别名为 Agent 族: " + field.getName() + "=" + value + " 含 " + alias);
            }
            // ③ 禁 cognition 前缀（X-18 → R-43）。
            assertFalse(value.contains("/cognition"),
                    "禁 /api/v1/cognition 前缀（认知正典 = /api/v1/cognitive）: " + value);
        }
        // 覆盖度自检：§5.2 首批 25 个 operationId 路径常量 + 1 能力探测常量必须在单源里可枚举。
        assertTrue(pathConstants.size() >= 26,
                "WAgentApiPaths 端点常量数量异常（应 ≥26：25 端点 + progress 能力位），实测 "
                        + pathConstants.size());
    }

    @Test
    @DisplayName("F10-01：WAgent* 生产类只能住在 ..engine.ai.wagent 包下（禁散落 agent 包）")
    void wAgentClassesResideInWagentPackageOnly() {
        JavaClasses imported = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.engine.ai");
        ArchRule rule = classes()
                .that().haveSimpleNameStartingWith("WAgent")
                .and().haveNameNotMatching(".*Test")
                .should().resideInAPackage("com.chinacreator.gzcm.engine.ai.wagent..");
        rule.check(imported);
    }

    @Test
    @DisplayName("§9.1.2：EventTypes.Agent 只加不改——既有 6 名逐值锁死，新名一律 PascalCase 且不复活 Agent 别名")
    void eventAgentDomainRegistrationIsAdditiveOnly() throws Exception {
        Set<String> legacySix = Set.of("AgentStarted", "ToolExecuted", "PlanGenerated",
                "AgentCompleted", "MissionCreated", "MissionCompleted");
        Map<String, String> names = new java.util.HashMap<>();
        for (Field field : EventTypes.Agent.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                names.put(field.getName(), (String) field.get(null));
            }
        }
        // ① 既有 6 名逐字未改（只加不删 IR03 在事件词表的落法）。
        assertTrue(names.values().containsAll(legacySix), "既有事件名被改动/删除: " + names.values());
        // ② PascalCase 单源风格 + 全局唯一。
        Set<String> values = new HashSet<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            assertTrue(e.getValue().matches("[A-Z][A-Za-z0-9]+"),
                    "事件名必须 PascalCase: " + e.getKey() + "=" + e.getValue());
            assertTrue(values.add(e.getValue()), "事件名重复: " + e.getValue());
        }
        // ③ W 域新名（非既有 6 名）禁把 W 域别名为 Agent：值里不得再出现 "Agent" 词。
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (legacySix.contains(e.getValue())) {
                continue;
            }
            assertFalse(e.getValue().contains("Agent"),
                    "W 域新增事件名被别名为 Agent（F10-01）: " + e.getKey() + "=" + e.getValue());
        }
        // ④ 30 名 additive 注册数核对（§9.1.2 批准口径：26 表源 + 4 有据补足）。
        assertEquals(6 + 30, names.size(),
                "EventTypes.Agent 常量总数应为既有 6 + W 域新增 30，实测 " + names.size());
    }
}
