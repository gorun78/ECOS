# ai-engine-impl — AI 引擎·实现层

> 子模块: ai-engine/impl | 端口: 共享父模块 18084 | 依赖: runtime/llm-gateway, cognitive-engine-api, kb-engine-api, PostgreSQL (sys_man)
> 上层: 见 ../AGENTS.md（ai-engine 顶层）

## 本模块干什么
- **实现层（业务）**：Agent 循环 (AgentLoopService) + 工具执行 (ToolExecutorService) + 会话 (AgentSessionService) + 委托 (AgentDelegationService) + KAG 知识抽取 (KnowledgeExtractorService) + LLM 网关适配 + SSE 流式 + 6 个核心测试。
- 测试充分（6 个 test class / 40 case）：`AgentMemoryTest` / `LLMProviderServiceTest` / `KnowledgeAgentContextTestCase` / `AgentCircuitBreakerTest` / `AgentLoopResultContractTest` / `ArchitectureTest`。

## 主要 code（控制器/服务/实体）
- `AgentLoopController` — Agent 对话 (stream=false→JSON / stream=true→**SSE** 流式) + 会话 CRUD (`GET/POST /api/v1/agent-loop/chat`, `/api/v1/agent-loop/sessions/{id}/chat`)。
- `AgentChatController` — 对话入口（与 LoopController 双 Controller 拆分：Loop 仅限 chat/stream，Chat 做会话持久化）。
- `SkillController` — `/api/v1/ai-engine/skill/*` Skill CRUD。
- `AgentMeshController` / `CognitiveController` / `DiagnosticAgentController` / `NLQController` / `AgentMetricsController` / `AgentConfigController` — Agent 网格 + 诊断 + NLQ + 度量 + 配置。
- `AgentStudioController` / `AgentProfileController` / `AgentCallController` / `AgentToolController` / `AgentProviderController` / `AgentEvalController` — Agent 工作室/画像/调用/工具/Provider/评测。
- `AIPAgentController` / `AIPGuardrailController` / `AIPPipelineController` / `AIPModelController` — AIP（Agent Interaction Protocol）四件套。
- `CopilotController` — Copilot 集成（`/api/v1/copilot/...`）。
- `CronJobController` / `EvolutionController` / `GuardrailsApiController` / `ActionBridgeController` / `PromptCompilerController` — 调度/进化/Guardrail/Action 桥/Prompt 编译。
- `ClassificationController` / `CognitiveConfigController` / `AiEngineStatusController` / `AiEngineAliasController` — 分类/认知配置/状态/别名。
- 服务：`AgentLoopService`（多轮工具调用循环，think→act→observe→think, **上限 5 轮**）/ `ToolExecutorService`（SQL/REST/**BUILTIN** 三种执行模式, 30s 超时）/ `AgentSessionService`（PG 持久化会话, 30 min 空闲过期）/ `AgentDelegationService`（`delegate_to_agent` 委托子 Agent, **单层禁止递归**）/ `KnowledgeExtractorService`（KAG 抽取: LLM→实体+关系+规则→SubGraph）/ `AgentMemory` + `CircuitBreaker`（熔断：LLM 失败 3 次降级）/ `KnowledgeAgentContext`（上下文）。
- 实体表：`sys_agent_session` / `sys_agent_message`（MyBatis DAO 在 `com.chinacreator.gzcm.engine.ai.session` / `.message` 子包）。
- ⚠️ 现状合规风险：`agent/mesh/knowledge/Neo4jQueryService.java` `import org.neo4j.driver.*`（**未直接 new**，通过注入 `Neo4jClient` 共用 runtime-access，新代码不得再 `import org.neo4j.driver.*`）。
- PG 复用表：`compliance_rules`（**只读**，cognitive-engine 用）；本 impl 内禁止写该表。

## 调用链（只读 + 调谁）
- → 同 engine api: 注入 `SkillService` / `AgentMeshService` / `GuardrailsService` / `ActionBridgeService` / `PromptCompilerService` / `CronJobService` 接口（来自 `ai-engine-api`）。
- → llm-gateway: 走 `LLMGatewayService`（架构铁律 2.5：禁止每个引擎直接调 LLM Provider API）。
- → cognitive-engine: REST `POST :18089/api/v1/knowledge/reason`（委托 `CognitiveService`，不 import cognitive-engine-impl，架构铁律 2.1）。
- → kb-engine: REST `POST :18086/api/v1/kb/graph/{query,nodes,edges}` + `POST :18086/api/v1/kb/rag`（KAG 抽取后 SubGraph 同步 KG）。
- → security-engine: 写操作前调 `POST :18081/api/v1/security/policy-engine/evaluate`（ABAC）+ `POST :18081/api/v1/audit`（审计）；同时 `security` 不可用时**默认 DENY**（架构铁律 2.4）。
- ← 被调用方: gateway 聚合加载、前端 `/api/v1/agent-loop/*`、`/api/v1/knowledge/extract/*` 等多个 API。
- 数据流：上游 `KnowledgeOptimizerService`（KAG）→ 抽取实体/关系/规则 → 调 kb-engine KG 同步；自身不持久化抽取结果（只有 Agent session/message 持久化）。

## 端点 / 补丁
- 路径池：
  - `/api/v1/agent-loop/chat` — Agent 对话（stream=true 走 SSE）。
  - `/api/v1/agent-loop/sessions` + `/api/v1/agent-loop/sessions/{id}` + `/api/v1/agent-loop/sessions/{id}/chat` — Session CRUD + 会话内对话。
  - `/api/v1/knowledge/extract` + `/sources`（GET）+ `/history`（GET）— 知识抽取三件套。
  - `/api/v1/knowledge/reason` — 混合推理委托（cognitive-engine）。
  - `/api/v1/ai-engine/skill/*` — Skill CRUD。
  - `/api/v1/copilot/*` — Copilot。
  - `/api/v1/chats/*`（隐式别名 `AiEngineAliasController`）。
- 示例（AgentLoopController SSE 片段）：
```java
@RestController
@RequestMapping("/api/v1/agent-loop")
public class AgentLoopController {
    private final AgentLoopService agentLoop;
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest req, HttpServletResponse resp) {
        // stream=true → SSE 流式；stream=false → 返回 JSON
        // Agent Loop 上限 5 轮，超过直接 [DONE]
    }
}
```

## 禁止
- **不直接 import 其他 engine-impl**（架构铁律 2.1，违反 = 验收失败）。跨引擎一律 REST。
- **不改 `LLMGatewayService` 接口**（架构铁律：LLM 网关 ontology 不变，禁止本 impl 内重写）。
- **Agent Loop 上限 5 轮**（顶层红线 #3，超过直接截断，不扩顶）。
- **不引入非 Java 依赖**（顶层红线 #4：pgvector `**不用 Python`；向量嵌入走 Java 客户端）。
- **Delegation 单层**（顶层红线 #5：`delegate_to_agent` 单层，禁止 `delegate_to_agent` 内再委托）。
- 不直接 LLM Provider API（统一走 `llm-gateway` LLMGatewayService）。
- 不直接 `new org.neo4j.driver.Driver`；现状 `Neo4jQueryService` `import org.neo4j.driver.*` 需 Wave 5 迁 runtime-access；新代码禁止。
- 不硬编码 token / BOD / metadata（LLM API key 走 `llm-gateway` 配置 + 网关，不在 Service 字面量）。
- 不写 `compliance_rules` 表（只读，cognitive 复用）。
- 实体新提自有 driver 禁止（治理）。
- 不直接 `new ScheduledExecutorService`（委托 `runtime-task`，架构铁律 2.5）。
