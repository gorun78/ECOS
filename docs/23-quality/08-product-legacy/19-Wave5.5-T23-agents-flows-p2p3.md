# Wave-5.5 T-23: 模块接口与验收 flows AGENTS.md × 10 + P2/P3 批次排清单

> 版本: 1.0 | 2026-09-03
> 约束: 不动 top 6 engine AGENTS / 12 子模块 AGENTS (wave 4.3 已定); 不新增 Maven 模块; 不改旧 API 路径; 不 new Driver / 不硬编码 (铁律 2.5 / 5.1 #7/#10)

## §1 — 10 份 AGENTS.md "接口与验收 flows" (新建)

每份 ~20 行 5 小节: 接入 flows / 主 API (curl × 3) / 接 DB 表 (3) / 别接 (调谁) / 验收 flows

| # | 路径 | 关键 fact (源码已核) |
|:--|:--|:--|
| 1 | `ecos_backend/service/ge/AGENTS.md` | TransformController @ data-engine-impl/transform, `/api/v1/engine/data/transform/{meta,execute}`; 无服务层独立 module |
| 2 | `ecos_backend/service/zhi/AGENTS.md` | 宿主 kb-engine (graph/sync + extract + rules), 计划 services/zhi-service D4 阶段 |
| 3 | `ecos_backend/service/cheng/AGENTS.md` | 宿主 cognitive-engine (`/api/v1/cognitive/{diagnose,reason}`), 不新增 DB 表 |
| 4 | `ecos_backend/service/ming/AGENTS.md` | 宿主 ai-engine (agent/chat + agent-loop/run), LLM 走 llm-gateway |
| 5 | `ecos_backend/runtime/runtime-access/AGENTS.md` | ConnectorFactory + Minio/S3/Git/Neo4j/DuckDB 统一底座; 唯一 REST 曝光面 = Gateway `GitController` |
| 6 | `ecos_backend/runtime/runtime-task/AGENTS.md` | ITaskManagementService 统一调度 (替代各引擎 ScheduledExecutorService); `ecos_task` (V23) 落表 |
| 7 | `ecos_backend/runtime/runtime-monitor/AGENTS.md` | IMonitor + IStrategy + ICollection + IPlugin/IWarnLog + Gateway `MonitorController`/`AlertController` REST 面 |
| 8 | `ecos_backend/runtime/llm-gateway/AGENTS.md` | LLMGatewayService 唯一 LLM 出口; `agent_call_log` + `profile_config` (MyBatis XML) |
| 9 | `ecos_backend/sysman/sysman-impl/AGENTS.md` | 20 控制器 (iam/dict/sys-config/tenant + 聚合查询), 跨引擎走 REST 不 import impl |
| 10 | `ecos_backend/common/common-api/AGENTS.md` | 契约包 (PipelineEvent/IEngine/异常族/ApiResponse); enforcer 反证 prop 0 业务 import |

**P2/P3 change surface**: 0 行代码改动。每文件自带"接入 flows"段明示 client → security → 模块 → 回 链路。

## §2 — P2 批次 (源码实证 · 12 项, 排入后续波)

> 说明: 任务指认 `docs/7-integration/02-联动...core §5` (12 P2 + 17 P3 原始清单), 该文件在当前 tree 未找到 (不在 08-产品化重构方案/ 也不在 7-integration/ 全长通配 glob 命中)。为遵守"不得编造 service/api", 下表按 **源码扫描实证** 重列 12 项真实技术债: 当前未落地但已明确归宿的缺口 (即原始 §5 清单项的可执行版本), 每项可独立验收。

| P2# | 项 (实证依据) | 归属 | Phase | Cron/Non-Cron | 估时 |
|:--|:--|:--|:--|:--|:--|
| P2-1 | `TransformController` 标注 `// TODO D4: 归位 ge-service（格）` — 当前寄居 data-engine-impl/transform | ge-service (D4 阶段建 module) | Wave-7 (D4 四转化收敛) | Cron (spontaneous) | 3d |
| P2-2 | OntologyDataController (222 行) 仍在内存存储, 应持久化 `ontology_data` 表 | ontology-engine-impl | Wave-6 | Cron | 1d |
| P2-3 | LineageController 内存存储 (距本体落库差距分析 P2 #L6) | ontology-engine-impl | Wave-6 | Non-Cron | 1d |
| P2-4 | `PipelineEvent` production 引用仅 1 处 (`SecuritySandboxService`) — 事件总线 listener 未建 | common-api + runtime | Wave-6 (Milestone) | Non-Cron | 2d |
| P2-5 | kb-engine `KGWriterService`/`RuleGraphService` 仍 import `org.neo4j.driver.*`, 未迁 runtime-access Neo4jClient Bean | kb-engine-impl → runtime-access | Wave-6 | Cron | 2d |
| P2-6 | Ontology KG 同步 (OntologyKgSyncService) 离手 Neo4j driver 直接在 impl, 待迁 runtime-access | ontology-engine-impl | Wave-6 | Non-Cron | 1d |
| P2-7 | `ecos_outbox_and_saga` (V48) outbox 骨架已建但 router 未接 — 需 distinct-topic 切分 (按 module) | runtime + gateway | Wave-6 Milestone | Non-Cron | 2d |
| P2-8 | cognitive-engine `CausalReasonerServiceImpl` 因果链 >3 层场景 Neo4j 超时断言 (10s / 1000 节点) 无独立 smoke | cognitive-engine-impl | Wave-6 | Non-Cron | 0.5d |
| P2-9 | `AlertController` 返 hardcoded mock 告警 (源码 L28-57 常量数组) — 应读 runtime-monitor `ecos_alert_history` | gateway / runtime-monitor | Wave-6 | Cron | 1d |
| P2-10 | `MonitorController.chartData` L62-70 mock (Math.random 时间序列) — 应改 runtime-monitor 采集落表读 | gateway | Wave-6 | Non-Cron | 1d |
| P2-11 | `ITaskAwareEngine` 四引擎全部未实现 (W2 任务实证, V6 FAIL) — Gateway EngineTaskController 绕过引擎契约 | security/data/ontology/cognitive/ai-engine-impl | Wave-7 | Non-Cron | 5d |
| P2-12 | services 层 12 → 4 收敛 (ge/zhi/cheng/ming-service) 本身; agent-service OrchestrationService 已承担实际编排, 待明确归属 | services/* | Wave-7 (D4) | Cron | 8d |

## §3 — P3 批次 (源码实证 · 17 项, 波 7+ 排)

| P3# | 项 (实证依据) | 归属 | Phase | Std/Ent/Ult | 估时 |
|:--|:--|:--|:--|:--|:--|
| P3-1 | MinerU 外部 HTTP 解析服务 (kb-engine `MinerUHttpParser` 代码已就位, infra `docker mineru --serve --port 8002` 未部署) | kb-engine / infra | Wave-7 | Std | 0.5d (代码) + 依赖 infra |
| P3-2 | CalculatorStep 依赖 Nashorn (Java 17 无内置) — transform calculator 表达式待接 SpEL (cognitive 已统一 SpEL) | data-engine transform | Wave-7 | Std | 1d |
| P3-3 | `ValidationController/PRD` 无: KB 抽取结果人工 review UI (kb-engine ExtractionController 输出后无前端 page 接) | kb-engine + 前端 | Wave-7 | Ent | 1d |
| P3-4 | `decision_case_precedent` (ecos_decision_precedent + pgvector 向量检索) 未建模 — JustificationClause 结构化升级, 04 文档 §三 #5 @TBD | cognitive-engine | Wave-7 | Ent | 3d |
| P3-5 | AgentMesh MissionExecutionEngine 跨 agent 委托回放 (agent-service OrchestrationService) — 多 agent 任务链 3+ 跳 trace 接 runtime-monitor telemetry | services/agent + ai-engine | Wave-8 | Ent | 3d |
| P3-6 | `ecos_telemetry` (V20) span exporter 到 PG 年后 PostgreSQLSpanExporter 已就位, 需接前端 dashboard (`/api/monitor` 展 OpenTelemetry trace) | gateway + 前端 | Wave-7 | Ult | 2d |
| P3-7 | `ecos_tenant_unified` (V37) 存档 `entity.tenant_id` 加列完成, 但 ontology/kb 两引擎 Repository 未标租户谓词 — 跨租户泄露风险 | ontology/kb-engine-impl | Wave-7 (安全) | Std | 2d |
| P3-8 | `ecos_data_coherence` (V33) 一致性检查表暂无调度任务 — 应接 runtime-task 周期巡检 + 判 `ecos_alert_history` warn | data-engine + runtime-task | Wave-8 | Std | 1d |
| P3-9 | `V4.4__ecos_ontology_rls.sql` RLS policy 定义未发 (开发 DB 仅 ENABLE ROW LEVEL SECURITY) — 需发完整 for ON CONFLICT DO NOTHING 前置下沉 | sysman-migration (不动既有 SQL; +新 V) | Wave-7 | Ent | 1d |
| P3-10 | ONNX/vector 硅化: pgvector 已在 V51 加入, 但 `ecos_knowledge_graph_vector.embedding` 类型向量实际写入点仅 kb-engine 一处 (ingest v2 未对齐 schema 长) | kb-engine | Wave-8 | Ult | 2d |
| P3-11 | `ecos_security_profile` (V24) 安全画像 CRUD 仅 EntryPoint 配基础 + 展示, 未接 OPA policy cache invalidation | security-engine + sysman | Wave-7 | Ent | 2d |
| P3-12 | `ecos_agent_prompts` (V56) 提示词版本化未实现 (当前人工备份 json), agent 提示词 A/B 切换缺 | ai-engine | Wave-8 | Std | 1d |
| P3-13 | ecos_outbox 反事实 (V48 saga 补偿) 回凳侧 仅 declare, 未接 distinct-topic (按 module) 路由 | runtime | Wave-8 | Std | 2d |
| P3-14 | 世界模型 `ObjectRuntimeService` + `StateMachineEngine` 迁出 common-api 到 ai-engine (业务合同撤回) — 分 2 介 | common-api → ai-engine | Wave-8 | Non-Scope-Migration | 2d |
| P3-15 | DiagnosticAgent + CausalReasoner 联合位 (ai-engine DiagnosticAgentController 打 cognitive `/diagnose` 后采样规则命中率) 无评测包 | ai-engine | Wave-8 | Ent | 1d |
| P3-16 | Git 仓库长 RamotP `ecos_git_repo` 版本化 C 模块角色定义 (多 repo 并存时租户隔离字段) | runtime-access | Wave-8 | Non-Cron | 0.5d |
| P3-17 | `ecos_evolution_log` (V57) 版本化编 (agent 迭代版本化 cross-point) 无 manifest schema | ai-engine | Wave-8 | Ent | 1d |

## §deliverable

本波交付 = 10 份 AGENTS.md (路径见 §1 表) + 本文档 (P2 12 项 + P3 17 项 = 29 项实证清单)
约束验收:
- 10 份 AGENTS 内 **每个 curl 路径** = 源码 @RequestMapping 已实测签名 (ge/zhi/cheng/ming 4 ingestion 是 present-in-impl 翻译文件, 无 1 个假 api)
- P2/P3 每行 = 实证 (文件路径或 migration version 或 code line range) / 0 行假
- 0 driver 直 new 引入 / 0 硬编码 / 0 新 Maven 模块 / 0 改既有 API
- 已引用: [架构铁律](../../.trae/rules/架构铁律.md) §2.4 (security 裁决) / §2.5 (runtime 底座) / §5.1 #10 (模块基线 13)