package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B3 白名单对账 + 受控执行门：
 * ① 工具总表逐个注册进 ToolRegistry（改名/漏注册即红）；② T2/T3 声明 {@code operation=write}
 * （裁决载荷声明优先）；③ T3 未带 {@code confirm=true} 被拒（不发起任何外呼）；
 * ④ T4 高危面（审批/删除/发布/密钥/冒充）零注册。
 */
@DisplayName("Copilot 工具注册器：注册对账 / write 声明 / T3 确认门 / T4 零注册")
class CopilotToolRegistrarTest {

    private ToolRegistry registry;
    private CopilotToolRegistrar registrar;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(null);
        CopilotToolSupport support = new CopilotToolSupport(null);
        registrar = new CopilotToolRegistrar();
        ReflectionTestUtils.setField(registrar, "toolRegistry", registry);
        ReflectionTestUtils.setField(registrar, "support", support);
        registrar.register();
    }

    @Test
    @DisplayName("工具总表全量注册：每个智能体白名单 ⊆ ToolRegistry（改名/漏注册即红）")
    void everyCatalogToolIsRegistered() {
        for (CopilotToolCatalog.Tool tool : CopilotToolCatalog.all()) {
            ToolSchema schema = registry.get(tool.name());
            assertNotNull(schema, "未注册进 ToolRegistry: " + tool.name());
            assertEquals(tool.desc(), schema.getDescription(), tool.name() + " 描述漂移");
        }
        for (Map.Entry<String, List<String>> entry : CopilotToolCatalog.whitelistsByAgent().entrySet()) {
            for (String name : entry.getValue()) {
                assertNotNull(registry.get(name),
                        entry.getKey() + " 白名单工具未注册: " + name);
            }
        }
    }

    @Test
    @DisplayName("读写声明：T2/T3 一律 operation=write；T1 无声明（保留旧推断链）")
    void writeToolsDeclareOperation() {
        for (CopilotToolCatalog.Tool tool : CopilotToolCatalog.all()) {
            ToolSchema schema = registry.get(tool.name());
            if ("T2".equals(tool.tier()) || "T3".equals(tool.tier())) {
                assertEquals("write", schema.getOperation(),
                        tool.name() + " 是写面工具，必须声明 operation=write（防按只读裁决）");
            } else {
                assertTrue(schema.getOperation() == null || schema.getOperation().isBlank(),
                        tool.name() + " 是只读工具，不应声明 write");
            }
        }
    }

    @Test
    @DisplayName("T3 确认门：未带 confirm=true 直接拒绝，错误信息引导用户确认")
    void tier3RequiresConfirmation() {
        CopilotToolCatalog.Tool tier3 = CopilotToolCatalog.all().stream()
                .filter(t -> "T3".equals(t.tier()))
                .findFirst().orElseThrow();
        var result = registry.execute(tier3.name(), Map.of());
        assertFalse(result.isSuccess(), tier3.name() + " 未确认不得执行");
        assertTrue(result.getError() != null && result.getError().contains("confirm"),
                "拒绝信息须指明 confirm=true，实际: " + result.getError());
    }

    @Test
    @DisplayName("T3 确认门：缺路径参数在确认后仍如实失败（不外呼不编造）")
    void tier3WithConfirmButMissingParamFailsHonestly() {
        CopilotToolCatalog.Tool tier3 = CopilotToolCatalog.all().stream()
                .filter(t -> "T3".equals(t.tier()) && t.path().contains("{"))
                .findFirst().orElseThrow();
        var result = registry.execute(tier3.name(), Map.of("confirm", true));
        assertFalse(result.isSuccess(), tier3.name() + " 缺路径参数应失败（端点不可达也不得编造）");
    }

    @Test
    @DisplayName("T4 高危面零注册：审批/发布/回滚/删除/密钥/冒充不得出现在工具面")
    void tier4NeverRegistered() {
        List<String> forbidden = List.of(
                "approve", "reject", "publish", "rollback", "deprecate", "delete",
                "rotate", "impersonate", "kill", "write-channel", "sync-all");
        for (CopilotToolCatalog.Tool tool : CopilotToolCatalog.all()) {
            String lower = tool.name().toLowerCase();
            for (String banned : forbidden) {
                assertFalse(lower.contains(banned),
                        "T4 动作进入工具面: " + tool.name());
            }
        }
        for (String banned : List.of("data_delete_asset", "ontology_approve_proposal",
                "security_rotate_key", "scenario_approve_action")) {
            assertTrue(registry.get(banned) == null, "高危工具不得注册: " + banned);
        }
    }
}
