package com.chinacreator.gzcm.engine.ai.copilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Copilot 内置智能体目录（6 个：数据 / 本体 / 知识 / AI / 场景 / 安全）。
 *
 * <p><b>单源收敛</b>：优先读运行期注册表（{@code ecos_agent_registry}，{@code role=builtin} 且
 * {@code metadata.kind=copilot}，60s TTL；工作台保存后调用 {@link #invalidate()} 即时生效）；
 * 注册表不可用/为空时回落 {@link CopilotSeedCatalog} 静态种子。</p>
 *
 * <p>{@code systemPrompt}（SOUL）只留在服务端供 agent-loop 使用，{@link #toWire} 不下发；
 * 工具白名单真值 = {@code metadata.toolWhitelist}（种子初值来自 {@link CopilotToolCatalog}）。</p>
 */
@Component
public class CopilotAgentCatalog {

    private static final Logger log = LoggerFactory.getLogger(CopilotAgentCatalog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_MS = 60_000L;

    /** 智能体定义。 */
    public record Definition(
            String id,
            String name,
            String engine,
            String description,
            List<String> routes,
            List<String> tools,
            List<String> quickQuestions,
            String systemPrompt) {
    }

    @Autowired(required = false)
    private ObjectProvider<AgentRegistryRepository> registryProvider;

    private record CacheEntry(long at, List<Definition> definitions) {
    }

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public List<Definition> all() {
        CacheEntry entry = cache.get();
        if (entry != null && System.currentTimeMillis() - entry.at() < CACHE_TTL_MS) {
            return entry.definitions();
        }
        List<Definition> loaded = load();
        cache.set(new CacheEntry(System.currentTimeMillis(), loaded));
        return loaded;
    }

    public Definition find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return all().stream().filter(d -> d.id().equals(agentId)).findFirst().orElse(null);
    }

    /** 按当前页面路径推断默认智能体（最长前缀；无命中回 AI）。 */
    public Definition forRoute(String pathname) {
        String path = pathname == null ? "" : pathname;
        Definition best = null;
        int bestLen = -1;
        for (Definition def : all()) {
            for (String route : def.routes()) {
                if (path.equals(route) || path.startsWith(route + "/")) {
                    if (route.length() > bestLen) {
                        best = def;
                        bestLen = route.length();
                    }
                }
            }
        }
        return best != null ? best : find(CopilotToolCatalog.AI);
    }

    /** 工作台保存后调用：清缓存使下一次读取立即看到新配置。 */
    public void invalidate() {
        cache.set(null);
    }

    // ─── 装载：注册表优先，种子兜底 ──────────────────────────────

    private List<Definition> load() {
        AgentRegistryRepository repo = registryProvider == null ? null : registryProvider.getIfAvailable();
        if (repo != null) {
            try {
                List<Definition> fromRegistry = new ArrayList<>();
                for (AgentRegistryEntity entity : repo.findAll()) {
                    Definition def = fromEntity(entity);
                    if (def != null) {
                        fromRegistry.add(def);
                    }
                }
                if (!fromRegistry.isEmpty()) {
                    return sortById(fromRegistry);
                }
            } catch (RuntimeException e) {
                log.warn("[CopilotCatalog] 注册表读取失败，回落种子目录: {}", e.getMessage());
            }
        }
        return fromSeeds();
    }

    private static List<Definition> sortById(List<Definition> defs) {
        List<String> order = List.of(CopilotToolCatalog.DATA, CopilotToolCatalog.ONTOLOGY,
                CopilotToolCatalog.KNOWLEDGE, CopilotToolCatalog.AI, CopilotToolCatalog.SCENARIO,
                CopilotToolCatalog.SECURITY);
        defs.sort((a, b) -> Integer.compare(order.indexOf(a.id()), order.indexOf(b.id())));
        return List.copyOf(defs);
    }

    private static List<Definition> fromSeeds() {
        List<Definition> defs = new ArrayList<>();
        for (CopilotSeedCatalog.Seed seed : CopilotSeedCatalog.all()) {
            defs.add(new Definition(seed.id(), seed.name(), seed.engine(), seed.description(),
                    seed.routes(), CopilotToolCatalog.namesForAgent(seed.id()),
                    seed.quickQuestions(), seed.systemPrompt()));
        }
        return List.copyOf(defs);
    }

    /** 注册表实体 → 定义（仅收 {@code role=builtin} 且 {@code metadata.kind=copilot}）。 */
    private static Definition fromEntity(AgentRegistryEntity entity) {
        if (entity == null || entity.getId() == null || !"builtin".equalsIgnoreCase(entity.getRole())) {
            return null;
        }
        try {
            JsonNode meta = entity.getMetadata() == null || entity.getMetadata().isBlank()
                    ? MAPPER.createObjectNode() : MAPPER.readTree(entity.getMetadata());
            if (!"copilot".equals(meta.path("kind").asText(null))) {
                return null;
            }
            List<String> routes = toStringList(meta.path("routes"));
            List<String> tools = meta.has("toolWhitelist")
                    ? toStringList(meta.path("toolWhitelist"))
                    : toolsFromCapability(entity.getCapability());
            List<String> quickQuestions = toStringList(meta.path("quickQuestions"));
            return new Definition(
                    entity.getId(),
                    entity.getName() == null ? entity.getId() : entity.getName(),
                    meta.path("engine").asText("unknown"),
                    meta.path("description").asText(""),
                    routes,
                    tools,
                    quickQuestions,
                    meta.path("systemPrompt").asText(""));
        } catch (Exception e) {
            log.warn("[CopilotCatalog] 智能体 metadata 解析失败: id={} reason={}",
                    entity.getId(), e.getMessage());
            return null;
        }
    }

    private static List<String> toolsFromCapability(String capability) {
        try {
            if (capability == null || capability.isBlank()) {
                return List.of();
            }
            JsonNode node = MAPPER.readTree(capability);
            return toStringList(node.path("tools"));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String text = item.asText(null);
                if (text != null && !text.isBlank()) {
                    list.add(text);
                }
            });
        }
        return List.copyOf(list);
    }

    // ─── 线形状（不含 systemPrompt） ─────────────────────────────

    public Map<String, Object> toWire(Definition def, boolean withQuestions) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("id", def.id());
        wire.put("name", def.name());
        wire.put("engine", def.engine());
        wire.put("description", def.description());
        wire.put("routes", def.routes());
        wire.put("tools", def.tools());
        if (withQuestions) {
            wire.put("quickQuestions", def.quickQuestions());
        }
        return wire;
    }
}
