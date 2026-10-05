package com.chinacreator.gzcm.ai.wagent.engine;

import java.util.*;

/**
 * 分册10 F10-02 · W Agent 编排域 Engine 属主单源常量（ADR-16 §2 "W 语义 ≠ Agent + 编排属主 = ai-engine"，
 * 解 WC-01/02/06 → R-31/R-32/R-37）。附件五大 Engine 名 ↔ ECOS 服务名 ↔ 前缀 ↔ 属主 REQ 的
 * 单源映射表。<b>不新建"第五套决策底座"</b>——Decision/Option/Action 属主 = ai-engine（本域），
 * cognitive {@code ecos_decision*} 定性为 Decision Basis（只读引用，不改表不改 API）。
 */
public final class WAgentEngineCatalog {

    private WAgentEngineCatalog() {}

    public record Engine(String name, String service, int port, String prefix, String ownerReq, String role) {}

    /** 附件五核心的 Engine 名 → ECOS 落位。禁另立 `agent-gateway`/`model-gateway`/`wagent-service` 等新模块（铁律 :292）。 */
    public static final List<Engine> ENGINES = List.of(
        new Engine("W-Agent Orchestrator", "ai-engine (aiming)", 18084, "/api/v1/wagent", "REQ-WAG-03", "orchestrator"),
        new Engine("Data Engine (D)",      "data-engine",       18082, "/api/v1/data",   "REQ-DAT-* (卷 02)", "probe"),
        new Engine("Ontology Engine (I)",  "ontology-engine",   18083, "/api/v1/ontology","REQ-ONTO-* (卷 05/07)","probe"),
        new Engine("Knowledge Engine (K)", "kb-engine",         18086, "/api/v1/kb",     "REQ-KB-06~09 (卷 04)","probe"),
        new Engine("Cognitive Engine (C)", "cognitive-engine",  18089, "/api/v1/cognitive","REQ-COG-06~09 (卷 05)","probe"),
        new Engine("Security Engine",      "security-engine",   18081, "/api/v1/security","REQ-SEC-* (卷 07/03)","policy-guard"),
        new Engine("LLM Gateway",          "llm-gateway (runtime)", 18084, "/api/v1/llm-gateway", "REQ-AI-* (卷 06)", "llm"),
        new Engine("Runtime (task/event/monitor/access)", "runtime/*", 18084, "/api/v1/runtime", "PMO-C (卷 01)", "infra")
    );

    /** 属主断言：wagent 域对象只允许落在 ai-engine。此方法供 ArchUnit 测试 `wDomainObjectsOnlyInAiEngine` 调用。 */
    public static String ownerOf(String targetEngineName) {
        for (Engine e : ENGINES) {
            if (e.name().equalsIgnoreCase(targetEngineName)) return e.service();
        }
        throw new IllegalArgumentException("unknown engine: " + targetEngineName + " — 必须在 WAgentEngineCatalog 登记（W245/R-31）");
    }

    /** 前缀 → 属主引擎 反查（F10-03 三红线 #1 工具调用必走既有引擎端点，禁 curl 未知路径）。 */
    public static String engineForPrefix(String apiPath) {
        if (apiPath == null) throw new IllegalArgumentException("apiPath null");
        for (Engine e : ENGINES) {
            if (apiPath.startsWith(e.prefix())) return e.service();
        }
        throw new IllegalArgumentException("apiPath 未匹配到既知引擎前缀: " + apiPath + "（F10-02/W245）");
    }

    /** 前缀合法性守护（`WAgentEngineCatalogTest#everyEngineMapsToLiveServicePrefix` 依赖）。 */
    public static boolean isKnownPrefix(String p) {
        return ENGINES.stream().anyMatch(e -> e.prefix().equals(p));
    }
}
