# ai-engine — Agent运行时 + LLM网关 + 知识抽取 + W Agent 编排

> 端口: **18084** | PMO: **ecos-pmo** | 依赖: runtime/llm-gateway, kb-engine-api, cognitive-engine-api
>
> **术语辨析（F10-01 · ADR-16）：W Agent ≠ Agent。**
> 本模块同时承载：① 既有 `AgentLoopService` 等 23 处 agent 语义 Controller（legacy 编排面，
> 前缀 `/api/v1/agent*`），② 新增 **W Agent 编排域**（`com.chinacreator.gzcm.ai.wagent.*`，
> 前缀 `/api/v1/wagent/*`）。**W 语义 ≠ Agent**：W = 五引擎（D/I/K/C/W）中的
> "W·火·编排属主"，负责 Goal/Question/Run/Plan/Step/Tool/Policy/Candidate/Evidence/Decision/Action
> 八类控制面对象的**编排协调**（R-59① 仅协调，不做金额运算）；Agent 是并发执行工具调用的运行时门面。
> 二者并存但语义分层，注释/文档中**禁混用**"W Agent / AI / 智慧"（决策详见
> `docs/10-架构/术语辨析.md` 与分册10 §零 §一）。详见 `docs/10-架构/术语辨析.md`。

## W Agent 编排（分册10 · F10-01~24）
- **位于包**：`com.chinacreator.gzcm.ai.wagent.*`（不新增 Maven 模块）
- **前缀单源**：`WAgentApiPaths`（`/api/v1/wagent/**`，24 端点）
- **控制器**：`wagent/web/WAgent*Controller`（9 个 Controller，全部经 `wagent.WAgentOrchestratorFacade` 协调）
- **Bean 注册**：`wagent/web/WAgentBeansConfig`（`@Configuration`，领域类走 `@Bean` 单例化，不散落 `@Component`）
- **DDL**：`ecos_backend/gateway/src/main/resources/db/migration/V227__wagent_goal.sql` ~ `V240__wagent_evidence_claim.sql`
  （14 张 `ecos_ai.ecos_wagent_*`，本仓**离线可查**；不新增 `ecos_agent` schema）
- **前端单通道**：`ecos_frontend/src/services/wagentApi.ts` + `src/__tests__/wagentApi-single-channel.test.ts`
  （同 C181 gitService 单通道纪律；禁各页面 raw fetch `/api/v1/wagent/*`）
- **关键护栏**：
  - `WAgentForbiddenDependencyArchTest`（wagent 不 import engine..impl / JDBC；controller 必须落 web 子包；禁自建 `ScheduledExecutor`）
  - `WAgentDdlShapeComplianceTest`（gateway 侧走查 14 张 V227~V240 脚本形态）
  - `PolicyGuard` + `PromptInjectionGuard` + `NarrativeGuard`（安全护栏）
  - `candidate/BulkApproveGuard`（语义/口径/知识禁批量审批）
- **单测**：`src/test/java/com/chinacreator/gzcm/ai/wagent/**`（§二 各 F 的 `mvn -Dtest=*` 验收标识离线跑）

## 我负责的
- **AgentLoopService**: 多轮工具调用循环（think→act→observe→think，上限5轮）
- **ToolExecutorService**: SQL/REST/BUILTIN三种执行模式，30s超时
- **AgentSessionService**: PG持久化会话，30min空闲过期
- **AgentDelegationService**: 子Agent委托（`delegate_to_agent`内置工具，单层）
- **KnowledgeExtractorService**: KAG风格知识抽取（LLM→实体+关系+规则→SubGraph）
- **AgentLoopController**: 非流式对话 + SSE流式 + 会话CRUD

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/agent-loop/chat | POST | Agent对话(stream=false→JSON, stream=true→SSE) |
| /api/v1/agent-loop/sessions | POST | 创建会话 |
| /api/v1/agent-loop/sessions/{id} | GET | 会话详情 |
| /api/v1/agent-loop/sessions/{id}/chat | POST | 会话内对话 |
| /api/v1/knowledge/extract | POST | 知识抽取 |
| /api/v1/knowledge/extract/sources | GET | 抽取源类型列表 |
| /api/v1/knowledge/extract/history | GET | 抽取历史 |
| /api/v1/knowledge/reason | POST | 混合推理 |

## 我的数据库表
- sys_agent_session (id, agent_id, user_id, tenant_id, status, message_count, created_at, last_active_at)
- sys_agent_message (id, session_id, role, content, tool_calls, tool_results, tokens, created_at)

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| cognitive-engine | POST :18089/api/v1/knowledge/reason | 混合推理委托 |

## 禁止
1. 不直接import其他引擎的impl模块
2. 不改LLMGatewayService接口
3. Agent Loop上限5轮
4. 不引入非Java依赖
5. Delegation单层
