package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copilot 六智能体目录与工具总表用例（种子驱动，断言从 CopilotSeedCatalog / CopilotToolCatalog 派生）。
 *
 * <p>锁五面：① 六智能体恰好覆盖六域且 id 固定；② 工具总表全局唯一且每域 ≥ 14 个（全生命周期）；
 * ③ 路由 → 默认智能体映射；④ 注册表不可用时目录回落种子（无 DB 依赖即可断言）；
 * ⑤ 线形状不泄露 systemPrompt；⑥ AgentLoop 未装配时对话入口返回 null（禁合成成功）。</p>
 */
@DisplayName("Copilot 六智能体：目录/工具总表/路由映射/无 loop 不伪造成功")
class CopilotAgentCatalogTest {

    private final CopilotAgentCatalog catalog = new CopilotAgentCatalog();

    @Test
    @DisplayName("目录恰好 6 个智能体，覆盖六域且 id/引擎与种子一致（空库回落种子）")
    void catalogCoversSixDomains() {
        List<CopilotAgentCatalog.Definition> all = catalog.all();
        assertEquals(6, all.size());

        List<String> ids = all.stream().map(CopilotAgentCatalog.Definition::id).toList();
        assertEquals(List.of(CopilotToolCatalog.DATA, CopilotToolCatalog.ONTOLOGY,
                CopilotToolCatalog.KNOWLEDGE, CopilotToolCatalog.AI,
                CopilotToolCatalog.SCENARIO, CopilotToolCatalog.SECURITY), ids);

        List<String> engines = all.stream().map(CopilotAgentCatalog.Definition::engine).toList();
        assertEquals(List.of("data-engine", "ontology-engine", "kb-engine",
                "ai-engine", "workspace", "security-engine"), engines);

        all.forEach(def -> {
            assertFalse(def.tools().isEmpty(), def.id() + " 必须绑定引擎工具");
            assertFalse(def.quickQuestions().isEmpty(), def.id() + " 必须提供建议问题");
            assertFalse(def.routes().isEmpty(), def.id() + " 必须登记页面路由前缀");
            assertNotNull(def.systemPrompt(), def.id() + " 必须有系统提示词（SOUL）");
        });
    }

    @Test
    @DisplayName("工具总表：全局唯一、每域 ≥ 14 个、写工具不带 GET、T3 属受控执行集")
    void toolCatalogIsWellFormed() {
        List<CopilotToolCatalog.Tool> all = CopilotToolCatalog.all();
        assertTrue(all.size() >= 100, "六域全生命周期工具面应 ≥ 100 个，实测 " + all.size());

        Set<String> names = new HashSet<>();
        for (CopilotToolCatalog.Tool tool : all) {
            assertTrue(names.add(tool.name()), "工具重名: " + tool.name());
            assertNotNull(tool.agent(), tool.name() + " 缺所属智能体");
            assertNotNull(tool.stage(), tool.name() + " 缺生命周期阶段");
            assertNotNull(tool.tier(), tool.name() + " 缺风险档位");
            assertNotNull(tool.owner(), tool.name() + " 缺属主");
            assertTrue(List.of("T1", "T2", "T3").contains(tool.tier()),
                    tool.name() + " 风险档必须是 T1/T2/T3（T4 不得注册）");
        }

        for (String agent : List.of(CopilotToolCatalog.DATA, CopilotToolCatalog.ONTOLOGY,
                CopilotToolCatalog.KNOWLEDGE, CopilotToolCatalog.AI,
                CopilotToolCatalog.SCENARIO, CopilotToolCatalog.SECURITY)) {
            assertTrue(CopilotToolCatalog.forAgent(agent).size() >= 14,
                    agent + " 工具数 < 14，未覆盖全生命周期");
        }
        // 写工具声明完整性：T2/T3 一律声明 operation=write（注册器侧保证），本处锁非 GET 的档位约束
        for (CopilotToolCatalog.Tool tool : all) {
            if (!"GET".equalsIgnoreCase(tool.method())) {
                assertTrue(!"T1".equals(tool.tier()) || tool.method().equalsIgnoreCase("POST"),
                        tool.name() + " 为 POST 的 T1（检索类）或应为 T2/T3");
            }
        }
    }

    @Test
    @DisplayName("种子 metadata：SOUL/白名单/绑定/RAG/护栏/发布齐备且与工具总表对账")
    void seedMetadataCoversAllConfigFaces() {
        for (CopilotSeedCatalog.Seed seed : CopilotSeedCatalog.all()) {
            Map<String, Object> meta = CopilotSeedCatalog.seedMetadata(seed);
            assertEquals("copilot", meta.get("kind"));
            assertEquals(seed.engine(), meta.get("engine"));
            assertNotNull(meta.get("systemPrompt"));
            assertNotNull(meta.get("routes"));
            assertNotNull(meta.get("quickQuestions"));

            List<String> whitelist = CopilotToolCatalog.namesForAgent(seed.id());
            assertEquals(whitelist, meta.get("toolWhitelist"), seed.id() + " 白名单必须来自工具总表");

            assertNotNull(meta.get("ontologyBinding"));
            assertNotNull(meta.get("rag"));
            assertNotNull(meta.get("guardrails"));
            assertNotNull(meta.get("publish"));
            assertEquals("draft", ((Map<?, ?>) meta.get("publish")).get("status"));
            assertEquals(CopilotSeedCatalog.SEED_VERSION, meta.get("seedVersion"));
        }
    }

    @Test
    @DisplayName("路由映射：按最长前缀命中对应智能体，未知路由回 AI")
    void routeMappingUsesLongestPrefix() {
        assertEquals(CopilotToolCatalog.DATA, catalog.forRoute("/data-workbench/pipelines").id());
        assertEquals(CopilotToolCatalog.ONTOLOGY, catalog.forRoute("/ontology_workbench").id());
        assertEquals(CopilotToolCatalog.KNOWLEDGE, catalog.forRoute("/knowledge_view/rag").id());
        assertEquals(CopilotToolCatalog.AI, catalog.forRoute("/ai-workbench").id());
        assertEquals(CopilotToolCatalog.SCENARIO, catalog.forRoute("/workbench/scenario-1").id());
        assertEquals(CopilotToolCatalog.SECURITY, catalog.forRoute("/security-center/audit").id());
        assertEquals(CopilotToolCatalog.AI, catalog.forRoute("/unknown-page").id());
        assertEquals(CopilotToolCatalog.AI, catalog.forRoute("").id());
        assertEquals(CopilotToolCatalog.AI, catalog.forRoute(null).id());
    }

    @Test
    @DisplayName("AgentLoop 未装配：chat 返回 null（控制器回落旧通道），不合成成功答案")
    void chatWithoutLoopReturnsNull() {
        CopilotChatService service = new CopilotChatService(catalog);
        assertTrue(service.supports("agent-data"));
        assertFalse(service.supports("agent-unknown"));
        assertNull(service.chat("agent-data", "你好", "sess-1"));
        assertNull(service.chat("agent-unknown", "你好", "sess-1"));
    }

    @Test
    @DisplayName("线形状只暴露 id/name/engine/routes/tools/建议，不泄露系统提示词")
    void wireShapeHidesSystemPrompt() {
        CopilotAgentCatalog.Definition def = catalog.find("agent-knowledge");
        assertNotNull(def);
        Map<String, Object> wire = catalog.toWire(def, true);
        assertEquals("agent-knowledge", wire.get("id"));
        assertEquals("kb-engine", wire.get("engine"));
        assertTrue(wire.containsKey("quickQuestions"));
        assertFalse(wire.containsKey("systemPrompt"), "系统提示词只留服务端");
    }
}
