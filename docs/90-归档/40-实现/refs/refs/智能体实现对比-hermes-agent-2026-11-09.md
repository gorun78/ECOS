# ECOS 智能体实现现状与 hermes-agent 功能对比

> 来源: 用户指令（AI 工作台智能体改造批次） | 日期: 2026-11-09 | 责任人: AI 工作台研发（Agent 起草）
> 版本: v1.0
> 追溯: 本册为类 14 参考资料；ECOS 侧结论基于 2026-11-09 工作树源码实测，hermes-agent 侧基于 2026-11-08 HEAD `dcace04` 浅克隆实测

---

## 一、结论摘要

1. **定位不同，不能按同一把尺子打分**：ECOS 是企业认知操作系统的「受治理智能体」——智能体只是数据治理→知识图谱→Agent 落地链路的最后一环，重权限、审计、本体写回；hermes-agent 是个人「自进化」智能体——重长期记忆、技能沉淀、多平台触达、任意环境执行。
2. **ECOS 真实可用的链路**：`/api/v1/agent-loop/chat` 对话（JSON/SSE 同路径）、Agent CRUD（mesh/AIP 两套）、7 个内置工具执行（DB/REST/委托/文件四件套）、本体 Action 写回提案 + 人工审批、WAgent 编排、评估与指标、LLM 统一网关、护栏 fail-closed。
3. **ECOS 主要短板**：非原生 function calling（解析纯文本/正则）；伪流式；无长期记忆与技能沉淀；Pipeline 后端伪执行；无容器级沙箱；RAG 未接入对话链路；多 Agent 串行且意图路由靠关键词；Agent 版本回滚未上线。
4. **hermes-agent 强项**：原生 tool calling + 41 个核心工具 / 35 个 toolset、6 种执行后端（含 Docker/Modal/Daytona/Vercel 沙箱）、185 个技能、66 个可选 MCP 目录、长期记忆（MEMORY.md/USER.md + FTS5 会话检索 + Honcho）、上下文压缩、cron 定时、子代理并行、对话轨迹→训练数据飞轮。
5. **建议路线**：不照搬（定位不同）。P0 补齐「原生工具调用 / 真流式 / Pipeline 执行器 / RAG 接链路 / 记忆与技能沉淀」，P1 补「沙箱执行 / MCP 双向 / 多 Agent 并行与协作协议」，P2 可选「多平台网关 / 轨迹数据飞轮」。

---

## 二、ECOS 智能体实现现状

### 2.1 链路位置

```
AI 工作台（智能体 / 逻辑编排 / 调试台 / 评估监控 / 模型工具）
   │  React 19 + Express BFF
   ▼
gateway :8080 ──► services/aiming :18084（ai-engine：Agent Loop / 工具 / 会话 / AIP / WAgent / 评估）
   │                    ├─► runtime/llm-gateway（唯一 LLM 出口，多 provider）
   │                    ├─► security :18081（OPA/RLS/脱敏/审计，fail-closed）
   │                    ├─► kb-engine :18086（RAG/pgvector，未接入对话链路）
   │                    └─► ontology :18083（Action 写回提案 + 人工审批）
   ▼
PostgreSQL（sys_agent_session / sys_agent_message / ecos_agent_registry）
```

### 2.2 能力矩阵

| 能力 | 实现位置 | 成熟度 |
|:--|:--|:--|
| 对话主链路 `/api/v1/agent-loop/chat`（JSON+SSE） | `ecos_backend/engine/ai-engine/ai-engine-impl/.../ai/controller/AgentLoopController.java` | 真实 |
| SSE 七帧协议 meta→step→token→delta→citation→error→done | `runtime/common-api/.../common/stream/SseFrameContract.java`、`.../ai/stream/AgentLoopSseFrames.java` | 真实（帧契约） |
| think→act→observe 循环 | `.../ai/service/AgentLoopService.java:72`（`MAX_TURNS = 5`，可被 agent 配置覆盖） | 真实，上限 5 轮 |
| 流式输出 | `AgentLoopController.java:531-532`（生成完整回答后按段发送 + `Thread.sleep(10)`） | **伪流式** |
| 内置工具（7 个） | `.../ai/service/ToolRegistry.java:163,230,306,338,396,452,515`（query_db / invoke_rest / delegate_to_agent / read_file / write_file / search_files / patch） | 真实 |
| 原生 function calling | 无：`callLLM` 不传 tools，靠文本/代码块/正则解析工具意图 | **缺失（最大差距）** |
| 子 Agent 委托 | `delegate_to_agent`（单层禁递归） | 真实，单层 |
| Agent CRUD | `.../ai/controller/AgentMeshController.java`（`/api/agent-mesh/agents`）、`AIPAgentController.java`（`/api/v1/aip/agents`） | 真实（两套并行） |
| Agent 数据模型 | `.../ai/agent/mesh/entity/AgentRegistryEntity.java`（提示词/模型等塞 metadata JSON） | 真实但弱结构化 |
| 版本管理 | Git 归档 `agent-collab`（仅 commit/tag） | 部分：无版本表、无回滚 |
| 多 Agent Mission | `.../ai/agent/mesh/MissionExecutionEngine.java:38`（SUPERVISOR/PIPELINE，逐子任务串行真调） | 真实（串行） |
| 意图路由 | `AgentMeshServiceImpl.java:63` `routeIntentByLLM`（实为关键词打分） | 降级实现 |
| AIP Logic/Pipeline | `AIPPipelineController.java` + `AgentStudioService.java:331,351-352`（`Random` + `Thread.sleep` 伪执行） | **后端 mock** |
| WAgent 自主编排 | `.../ai/wagent/`（plan/step：tool/llm/human_gate/wait_task，kill-switch、run 事件流） | 真实（第二套体系） |
| 本体写回提案 + 人工审批 | `engine/ontology-engine/.../OntologyProposalController.java`（DRAFT/PENDING→APPROVED→EXECUTED + 双向核对） | 真实 |
| 会话持久化 | `.../ai/service/AgentSessionService.java`；表 `sys_agent_session` / `sys_agent_message` | 真实；流式分支不回写、历史不回灌 |
| 长期记忆 | `AgentLoopService.java:736-740`（`sessionMessages = null` 恒定，记忆注入被写死跳过） | **占位** |
| RAG（pgvector+HNSW） | `engine/kb-engine/.../RagController.java` | 真实但**未接入 Agent 链路** |
| 本体上下文（OAG 8 步） | `.../ai/oag/OagPipelineEngine.java`；`KnowledgeRetrieverNode` 为硬编码假片段 | 部分 |
| 护栏/脱敏/RLS | `.../ai/service/GuardrailsServiceImpl.java`（路由 security，fail-closed） | 部分（策略编译为占位） |
| LLM 统一出口 | `runtime/llm-gateway/.../gateway/LLMGatewayImpl.java`（多 provider、限流降级） | 真实 |
| 模型目录 | `AiModelCatalogController.java`（只读网关目录；注册接口 501） | 部分 |
| 评估 | `AgentEvalController.java` + `AgentEvaluator.java`（逐题跑 loop，五维启发式打分） | 真实（非 LLM judge） |
| 调度 | `CronJobController.java`（经外部 MCP provider，未装配返回 501） | 部分 |
| MCP | `services/agent-service/.../runtime/mcp/HermesMCPClient.java`（JSON-RPC 单向工具桥） | 存在但单向 |

### 2.3 前端（本批菜单调整后）

| 菜单 | 页面 | 能力与成熟度 |
|:--|:--|:--|
| 仪表盘 | `DashboardView.tsx` | KPI/审计流/评估（真实 eval 端点） |
| 智能体 | `ChatbotStudioView.tsx`（`/chatbot_studio`，原「会话机器人」） | 对话走 agent-loop 真实链路；CRUD 走 agent-mesh；提案确认真调 ontology；RAG 上传/发布为禁用态（能力未上线） |
| 逻辑编排 | `LogicView.tsx` | React Flow 画布真实；执行端点在后端为伪执行，无 nodeTraces |
| 调试台 | `AgentPlayground.tsx` | 走 `/api/v1/oag/chat/stream`；OAG 检索腿为假片段 |
| 评估监控 | `EvalsView.tsx` | 真实指标/错误流；eval 报告 UI 未接 |
| 模型工具 | `ModelCatalogView.tsx` | 目录/对比/护栏只读绑定 |

> 会话机器人页存在「中间 + 右侧」两处重复交互调试沙箱，已于 2026-11-09 删除中间列（原 `ChatHeader.tsx` 整列），仅保留右侧 ChatPanel 沙箱。

---

## 三、hermes-agent 概览（2026-11-08 HEAD `dcace04`，MIT）

个人助理型自进化 Agent，同一核心跑 CLI / TUI / Electron 桌面 / 20 余个消息平台；原生 Python 实现，仓库实测 23,140 个受控文件、10,982 个 `.py`。

| 能力 | 实现证据（hermes 仓内相对路径） | 备注 |
|:--|:--|:--|
| Agent 主循环 | `run_agent.py:241`（`max_iterations` 默认 `sys.maxsize` 不限轮）、`agent/turn_*.py` | 轮次预算可配，超限可续跑 |
| 工具面 | `toolsets.py:11`（41 个核心工具）、`toolsets.py:70`（35 个 toolset） | 终端/文件/浏览器/搜索/图像/语音/看板/计算等 |
| 执行后端 | `tools/terminal_tool_backends.py:52`（local, docker, modal, daytona, vercel_sandbox, ssh） | 含 serverless 休眠唤醒 |
| 技能系统 | `skills/` + `optional-skills/` 共 185 个 `SKILL.md`；`agent/curator.py`（定期整理） | 程序性记忆，兼容 agentskills.io |
| 长期记忆 | `tools/memory_tool.py:2`、`tools/memory_tool_store.py:224`（MEMORY.md / USER.md）；`tools/session_search_tool.py:5`（FTS5 跨会话检索）；Honcho 用户建模 | 跨会话持续积累 |
| 上下文压缩 | `agent/context_compressor*.py`、`compression_automation.py` | 压缩是唯一允许破坏缓存前缀的操作 |
| 委派/子代理 | `tools/delegate_tool*.py`、`tools/async_delegation*.py` | 隔离子代理并行，可指定 provider/model/限额 |
| 定时任务 | `cron/`（jobs / delivery_queue / incidents）、`cronjob_manage` 工具 | 自然语言定义、投递到任意平台 |
| 多平台网关 | `gateway/platforms/`；平台标识 telegram/discord/slack/whatsapp/signal/matrix/teams/feishu/dingtalk/email/sms/line + `api_server` | README 明示 5 类 IM + Email，AGENTS 称 ~20 平台 |
| MCP | `tools/mcp_tool*.py`、`optional-mcps/`（66 个目录） | 既做 MCP 客户端，也可反向暴露 |
| 审批与安全 | `tools/approval*.py`（human/smart/yolo、允许清单、审批地板）、`SECURITY.md` | 命令审批 + DM 配对 + 容器隔离 |
| 浏览器/电脑操作 | `tools/browser_*.py`、`plugins/computer_use/` | CDP 直控 + 无障碍树 |
| 训练数据飞轮 | `batch_runner.py`、`trajectory_compressor.py`、`evals/`（40 个子目录） | 轨迹批量生成→压缩→评测回归 |
| 扩展体系 | `plugins/`（19 类：memory/model-providers/platforms/delegation…）、`plugin-catalog/` | 核心窄腰、能力在边缘 |
| 界面 | `cli.py`、`ui-tui/`、`apps/desktop/`、`acp_adapter/`、`web/` | CLI/TUI/桌面/ACP/Web |

---

## 四、能力对比矩阵

| 维度 | ECOS | hermes-agent | 差距判断 | 优先级 |
|:--|:--|:--|:--|:--:|
| 对话循环 | 5 轮 think→act→observe，轮次可配 | 不限轮（预算）+ 中断续跑 + 溢出恢复 | hermes 更成熟 | P1 |
| 工具调用协议 | **纯文本/正则解析**，非原生 | 原生 tool calling（OpenAI/Anthropic 适配） | **ECOS 结构性差距** | **P0** |
| 工具生态 | 7 个内置（SQL/REST/委托/文件） | 41 核心 + toolsets + MCP 66 目录 | ECOS 面窄但受治理 | P1 |
| 流式 | 伪流式（整段切分 + sleep） | 真增量流 + 流式工具输出 | ECOS 短板 | **P0** |
| 长期记忆 | 写死跳过（`sessionMessages=null`） | MEMORY.md/USER.md + FTS5 + Honcho | ECOS 无 | **P0** |
| 技能沉淀 | 无（技能=CRUD 表） | 185 技能，任务后自动创建/自改进 | ECOS 无 | P0/P1 |
| RAG 接链路 | 引擎有，对话未接 | 上下文文件 + 会话检索 + 技能知识 | ECOS 未闭环 | **P0** |
| 多 Agent | Mission 串行 + 关键词路由 | 子代理并行、provider/model 独立 | hermes 更强 | P1 |
| 编排引擎 | Logic 画布真、执行 mock；WAgent 真 | 无独立 DAG 画布（Kanban/子代理替代） | **ECOS 独特且可保留** | **P0（修执行器）** |
| 执行环境 | 无容器沙箱（仅策略干跑） | 6 后端含容器/serverless | ECOS 缺 | P1 |
| MCP | 单向工具桥 | 客户端 + 服务端双向 + 目录 | ECOS 弱 | P1 |
| 模型接入 | llm-gateway 多 provider（真实） | provider 插件 + 任意 OpenAI 兼容端点 | 相当 | — |
| 权限治理 | OPA/RLS/RBAC/分级密级 + fail-closed | 审批模式 + 容器隔离（个人场景） | **ECOS 显著更强** | — |
| 审计与写回 | 本体 Action 提案 + 双向核对 + 审计 | 无企业级数据写回治理 | **ECOS 独特** | — |
| 评估 | 五维启发式 + 指标流 | evals 目录（含 compaction/回归） | ECOS 可用但浅 | P1 |
| 定时任务 | 端点存在，provider 未装配（501） | cron 完整 + 多平台投递 | ECOS 未通 | P1 |
| 多平台触达 | Web 工作台（+BFF） | 12+ 平台网关 | 定位不同，ECOS 非必需 | P2 |
| 数据飞轮 | 无 | 轨迹生成/压缩/评测闭环 | 可选借鉴 | P2 |

---

## 五、差距分析与借鉴建议

### P0（建议下一批补齐，直接影响「智能体是否好用」）

1. **原生 function calling**：`LLMGatewayService` 增加 tools/tool_choice 透传，`AgentLoopService` 改为消费 `tool_calls` 结构，删除正则推断分支；这是与 hermes 差距最大的一项。
2. **真流式**：接入 llm-gateway stream，token 帧直出，取代 `AgentLoopController.java:531-532` 的切段 + `Thread.sleep`。
3. **Pipeline 执行器**：为 `llm/tool/ontology/approval/condition/trigger` 六类节点实现真实执行与 nodeTrace，替换 `AgentStudioService.java:351-352` 的 `Random`+`sleep` 伪执行；ECOS 的编排画布是相对 hermes 的差异化优势，值得先修实。
4. **RAG / 本体上下文本真接入**：把 kb-engine `/api/v1/kb/rag` 接进 agent-loop 上下文组装，替换 OAG `KnowledgeRetrieverNode` 的硬编码片段。
5. **会话历史与记忆闭环**：历史回灌 + 修复 `AgentLoopService.java:736-740` 的记忆注入死代码；引入「记忆条目 + 检索」最小闭环（可先落 PG，不必照搬 MEMORY.md 文件形态）。

### P1（能力补强）

6. **技能沉淀**：借鉴 hermes `curator` 思路，把复杂任务成功轨迹沉淀为可复用提示词/工具序列（ECOS 已有 Skill CRUD 表可扩展）。
7. **沙箱执行**：工具执行（SQL/REST/文件）引入隔离执行边界与配额，降低高危工具风险。
8. **MCP 双向**：`HermesMCPClient` 由单向升级为可注册外部 MCP server 的客户端池，并把 ECOS 能力反向暴露。
9. **多 Agent**：Mission 支持并行子任务与结果汇聚；`routeIntentByLLM` 改为真实 LLM 路由。
10. **调度打通**：装配 cron provider，让端点不再 501。
11. **评估**：引入 LLM judge 与回归集（可参考 hermes `evals/` 的回归组织方式）。

### P2（可选）

12. 多平台网关（企微/钉钉/飞书投递）、轨迹数据飞轮（对话轨迹导出→压缩→评测），仅在产品需要时立项。

> 边界提醒：hermes 的「自动创建技能、跨会话自学习、任意命令执行」属个人助理形态，直接移植会与 ECOS 的 RBAC/审计/写回治理冲突——借鉴机制，不照搬形态。

---

## 六、证据与复现

ECOS 侧（仓库根执行，均已实测）：

```bash
grep -n "MAX_TURNS" ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentLoopService.java
grep -n "Thread.sleep\|伪流式" ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AgentLoopController.java
grep -n 'new ToolSchema("' ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolRegistry.java   # 7 个
grep -n "sessionMessages = null" ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentLoopService.java
grep -n "Random\|Thread.sleep" ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentStudioService.java
```

hermes-agent 侧（临时浅克隆，不入库；HEAD `dcace04` 2026-11-08）：

```bash
git clone --depth 1 https://github.com/NousResearch/hermes-agent.git <临时目录>
sed -n '11,60p' toolsets.py            # 41 个核心工具
grep -c '^    "[a-z_]*": _ts(' toolsets.py   # 35 个 toolset
grep -n '_BUILTIN_BACKENDS' tools/terminal_tool_backends.py   # 6 种执行后端
find skills optional-skills -iname 'SKILL.md' | wc -l          # 185
ls optional-mcps | wc -l                                       # 66
ls -d evals/*/ | wc -l                                         # 40
```

---

> 本文为参考资料（类 14），不构成排期；落地批次以 PMO 指令为准。
