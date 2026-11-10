package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * B2 目录读库：注册表为运行期单源——工作台编辑（metadata）保存后 invalidate 即生效。
 */
@DisplayName("Copilot 目录读注册表：编辑生效 + invalidate 失效缓存")
class CopilotAgentCatalogRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 可变内存注册表：updateVersion 用来模拟工作台保存。 */
    private static final class StubRepo implements AgentRegistryRepository {
        final Map<String, AgentRegistryEntity> store = new java.util.LinkedHashMap<>();
        final AtomicInteger reads = new AtomicInteger();

        private AgentRegistryEntity entity(String id, String prompt, String model) throws Exception {
            AgentRegistryEntity e = new AgentRegistryEntity();
            e.setId(id);
            e.setName(id);
            e.setRole("builtin");
            e.setStatus("ACTIVE");
            e.setMetadata(MAPPER.writeValueAsString(Map.of(
                    "kind", "copilot",
                    "engine", "kb-engine",
                    "systemPrompt", prompt,
                    "model", model,
                    "toolWhitelist", List.of("knowledge_search", "knowledge_rag"),
                    "routes", List.of("/knowledge_view"),
                    "quickQuestions", List.of("q1"),
                    "description", "d")));
            e.setCapability("{\"tools\":[\"knowledge_search\",\"knowledge_rag\"]}");
            return e;
        }

        void seed() throws Exception {
            store.put("agent-knowledge", entity("agent-knowledge", "旧SOUL", "deepseek-chat"));
        }

        void editPrompt(String id, String prompt) throws Exception {
            AgentRegistryEntity e = store.get(id);
            e.setSystemPrompt(prompt);
            store.put(id, e);
        }

        @Override public AgentRegistryEntity findById(String id) { return store.get(id); }
        @Override public List<AgentRegistryEntity> findAll() { reads.incrementAndGet(); return List.copyOf(store.values()); }
        @Override public List<AgentRegistryEntity> findActive() { return findAll(); }
        @Override public int insert(AgentRegistryEntity a) { store.put(a.getId(), a); return 1; }
        @Override public int update(AgentRegistryEntity a) { store.put(a.getId(), a); return 1; }
        @Override public int delete(String id) { return store.remove(id) == null ? 0 : 1; }
    }

    private static ObjectProvider<AgentRegistryRepository> provider(StubRepo repo) {
        return new ObjectProvider<>() {
            @Override public AgentRegistryRepository getObject() { return repo; }
            @Override public AgentRegistryRepository getObject(Object... args) { return repo; }
            @Override public AgentRegistryRepository getIfAvailable() { return repo; }
            @Override public AgentRegistryRepository getIfUnique() { return repo; }
        };
    }

    @Test
    @DisplayName("注册表编辑 SOUL 后 invalidate：目录立即返回新提示词与白名单（缓存不挡路）")
    void registryEditTakesEffectAfterInvalidate() throws Exception {
        StubRepo repo = new StubRepo();
        repo.seed();
        CopilotAgentCatalog catalog = new CopilotAgentCatalog();
        ReflectionTestUtils.setField(catalog, "registryProvider", provider(repo));

        CopilotAgentCatalog.Definition before = catalog.find("agent-knowledge");
        assertNotNull(before);
        assertEquals("旧SOUL", before.systemPrompt());

        // 工作台保存：改 SOUL + 白名单（派生 setter 读-改-写 metadata）
        AgentRegistryEntity e = repo.store.get("agent-knowledge");
        e.setSystemPrompt("新SOUL");
        repo.update(e);

        // 未失效（TTL 内）：仍旧值（证明缓存存在，避免每问一次查库）
        assertEquals("旧SOUL", catalog.find("agent-knowledge").systemPrompt());

        // 保存后 invalidate（AgentMeshController 已接线）→ 新值即时生效
        catalog.invalidate();
        CopilotAgentCatalog.Definition after = catalog.find("agent-knowledge");
        assertEquals("新SOUL", after.systemPrompt());
        assertEquals(List.of("knowledge_search", "knowledge_rag"), after.tools());
    }

    @Test
    @DisplayName("非 copilot 智能体（无 kind 标记）不进目录；空库回落种子六智能体")
    void nonCopilotAgentsExcludedAndEmptyFallsBackToSeeds() {
        StubRepo empty = new StubRepo();
        CopilotAgentCatalog catalog = new CopilotAgentCatalog();
        ReflectionTestUtils.setField(catalog, "registryProvider", provider(empty));
        assertEquals(6, catalog.all().size(), "空库回落种子目录");

        // 塞一个非 copilot builtin（旧 builtin-* 形态：metadata 无 kind）
        AgentRegistryEntity legacy = new AgentRegistryEntity();
        legacy.setId("builtin-data-agent");
        legacy.setRole("builtin");
        legacy.setStatus("INACTIVE");
        legacy.setMetadata("{}");
        empty.store.put("builtin-data-agent", legacy);
        catalog.invalidate();
        assertEquals(6, catalog.all().size(), "非 copilot/INACTIVE 实体不得混入目录");
    }
}
