# ECOS 场景工作台与认知引擎 — 现状审计

> 审计日期：2026-11-03
> 审计基线：`master` @ `811bd6e5`；设计输入 = `docs/c-design/` 四份文档
> 方法：直接阅读源码/DDL/测试并核对路径与行号；未运行功能不得标记为已验证。
> 标记口径：`EXISTS` / `PARTIAL` / `MISSING` / `CONFLICT` / `NOT_VERIFIED`

## 1. 实际模块、入口与职责

| 模块 | 路径 | 入口 | 职责（实测） |
|---|---|---|---|
| workspace（场景层，:18090） | `ecos_backend/workspace/workspace-impl` / `workspace-service` | `WorkspaceServiceApplication.java:119` | 场景 CRUD、绑定/连边、完整度、状态机、蓝图、运行、沙盘画布、E1~E4 代理 |
| cognitive-engine（:18089，由 gateway/aiming 双宿主承载） | `ecos_backend/engine/cognitive-engine/{cognitive-engine-api,cognitive-engine-impl}` | `CognitiveEngine2Application` | Context Builder、证据/假设/信念、认知模型注册、预测 run、反事实、Outcome 评估、E1~E4 查询 |
| ai-engine（W 层） | `ecos_backend/engine/ai-engine` | `services/aiming` | Agent Runtime、计划/工具执行（本任务不重写） |
| security-engine | `ecos_backend/engine/security-engine` + `services/sysman` | — | 权限/密级/审批裁决；PDP 不可用 fail-closed |
| 前端 | `ecos_frontend` | `src/main.tsx`（HashRouter） | L1 `/workbench`、L2 `/workbench/:scenarioId`、L3 forecast/decisions、沙盘画布、蓝图/认知四件套 |

## 2. 四份设计文档与代码逐项映射

### 2.1 场景工作台（详细设计-场景工作台 v1.0）

| 设计项 | 现状 | 证据 |
|---|---|---|
| Scenario Blueprint 版本化载体 | EXISTS | `workspace/.../blueprint/ScenarioBlueprint.java:28`；DDL `V271__blueprint_workspace.sql:23`（`ecos_control.ecos_scenario_blueprint`，`version`/`status`/`content_json`/`artifact_digest`） |
| 蓝图生命周期/发布不可变 | EXISTS | `ScenarioBlueprintStateMachine.java:56-96`、`ScenarioBlueprintService.java:281-314`（RELEASED 后只读、版本 +1 新行） |
| 六类绑定/三层/连边 | EXISTS | `BindingType.java:14-19`（DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/AI_AGENT/SECURITY_POLICY/INTERFACE）、`ScenarioTier.java:5-8`、`EdgeType.java:8-12`、`ScenarioBindingLinkService.java:33-34` |
| 完整度唯一算法（后端） | EXISTS | `ScenarioCompletenessService.compute:44-108`，`coverage = presentCount / requiredEdges.size()`（:97）；`FixedDenominatorEdgePolicy.java:23-26` 推导 E_req |
| 孤岛绑定阻断激活 | EXISTS | `IslandBindingGuard.findIslands:21`；`ScenarioStatusMachine.java:127-137` 激活三前置 |
| Gap 结构（含 blocking/severity） | PARTIAL | 蓝图 `gaps_text` 仅 JSON-TEXT 存储（`V271:36`），无独立查询/严重级门禁端点；`ScenarioPreValidateController.java:55` 只返回三项检查 |
| 沙盘/正式隔离 | PARTIAL / CONFLICT | `RunMode.java:7-8` + `ScenarioRunRequestValidator.java:22-37` 已在位，但 `ScenarioRunService.run:54-180` 未接收/落 runMode，`WorkspaceDataDao.insertRun:433-443` 无 `run_mode`；`SandboxWriteGuard.java:30-34` 全仓零调用 |
| 运行记录 | PARTIAL | `V125__ecos_scenario_run.sql:8`（`public.ecos_scenario_run`）；`ScenarioRunService` 会回写场景 `metrics`（沙盘同样回写 = 正式污染） |
| 场景版本/审计 | PARTIAL | 蓝图有版本；场景主表 `ecos_business_scenario` 无 version；`ScenarioStatusTransitionService.transition:74-92` 有审计 |

### 2.2 认知引擎（详细设计-认知引擎 v1.0）

| 设计项 | 现状 | 证据 |
|---|---|---|
| Context Builder | EXISTS（装配面不全） | `ContextBuilderService.java:78-138` 十步算法、`ContextDigestCalculator`、`ContextBuildController.java:49` `/api/v1/cognitive/context/build`；仅 `DataEngineContextSourceReader` 一个 reader，K/I/C reader 缺失（:374-376 判 503） |
| Evidence/Hypothesis/Belief | EXISTS（状态词表冲突） | `CognitiveEvidenceController`、`CognitiveHypothesisController`、`CognitiveBeliefController`；`HypothesisStatus.java` = PROPOSED/EVIDENCED/BELIEVED/REFUTED，但 `CognitiveHypothesisService.java:61,127-131` 仍写 `VALID/INVALIDATED` |
| Cognitive Model Registry | EXISTS | `ModelRegistryService`、`ModelRegistryDao`、`ForecastController.java:101-119`（registry/hash/gates/publish） |
| Cognitive Framework Registry | **MISSING** | 全仓无 framework 表/实体/服务/端点（grep `framework` 仅见设计文档与前端无关词） |
| Cognitive Method | **MISSING** | 无方法定义/关联模型 |
| Strategy Selector（硬约束过滤→排序→计划 DAG） | **MISSING** | `StrategyGeneratorService` 是 OAG 策略文本节点，不做框架选择；`CognitivePlannerController` 为内存因果链计划，无硬过滤/排除原因码/DAG 校验 |
| Plan Validator（环/契约/预算/能力/权限） | **MISSING** | 无类、无端点 |
| 执行状态机（CREATED…CANCELLED） | **MISSING** | 认知域无状态机；`CognitivePlannerController` 仅字符串 status=planned |
| 认知任务/执行记录/产物引用 | PARTIAL | 预测 run 在 `ForecastRunComputeController`（`/api/v1/cognitive/forecast-runs`）；长任务底座 `runtime-task` 存在但认知未接入执行状态 |
| Outcome Evaluator | EXISTS（数值接缝未装配） | `OutcomeEvaluateService`、`OutcomeEvaluateController.java:48-72`；`OutcomeMetricValueReader` 无 bean ⇒ `MISSING_VALUE_SEAM` |
| E1~E4 | EXISTS | `scenario/ScenarioCognitionController.java:43-106`，路径 `/api/v1/business/scenarios/{scenarioId}/cognition/{detect|operation-eval|hypotheses|beliefs}`；`CognitiveApiPaths` 单源 |
| 反事实/情景 | EXISTS | `CounterfactualController.java:48`、`ScenarioController`（simulate/compare）、`ForecastRunComputeController` |
| 因果不越界/金额不 LLM | EXISTS（有门禁） | `CognitiveNoLlmNumbersTest`、`CognitiveIronLawsTest`、`Cm07NumericGuardTest` |

### 2.3 实施任务与验收清单 / README 执行说明

两份文档是流程约束，无独立代码映射；其阶段 0 审计要求由本文件承接。禁止项（不新增 `cognitive_context`、不建第二套 Runtime、不直连供应商、不改既有 API）已核对：当前代码无 `cognitive_context` 表、无第二套 Agent Runtime、LLM 仅经 `llm-gateway`。

## 3. 功能清单（按实现状态）

### EXISTS（可复用，不重写）
- 场景蓝图生命周期、发布不可变、审批门（`ScenarioBlueprintService`）
- 绑定/连边/孤岛/必需边/完整度（`ScenarioBindingLinkService`、`ScenarioCompletenessService`、`EdgeContractProbe`）
- E1~E4 查询与状态码语义（`ScenarioCognitionController`、`cognitiveClient.ts`）
- Context Builder 十步 + digest + 错误矩阵
- Evidence/Hypothesis/Belief 存储与端点
- 预测 run、反事实、Outcome 评估
- 前端 L1/L2/L3、蓝图面板、认知四件套、沙盘画布、六态矩阵

### PARTIAL（需补齐）
- 沙盘隔离：枚举/校验器在位，运行持久化与正式写入隔离断链
- 完整度：算法在位，前端零消费（`/completeness` 无页面调用，e2e 全 skip）
- Context Builder：仅 DATA reader；K/I/C 源未接线导致必需源 503
- Gap：仅蓝图 JSON-TEXT，无严重级/阻断门禁接口
- 假设状态：词表与写路径不一致，EVIDENCED/BELIEVED/REFUTED 不可达

### MISSING（本任务必须新建）
- Cognitive Framework Registry（定义/版本/注册/发布/启停/适用范围）
- Cognitive Method（与框架关联）
- Strategy Selector（硬约束过滤 + 排除原因码 + 确定性排序 + 计划 DAG）
- Plan Validator（环/契约/能力/预算/权限）
- 认知执行状态机（非法迁移拒绝、终态、幂等、业务结论状态分离）

### CONFLICT（记录，按最小兼容处理）
1. `CognitiveHypothesisService` 写 `VALID/INVALIDATED` vs `HypothesisStatus` 四态冻结 — 不在本批改既有写语义（涉及存量数据迁移），登记遗留。
2. `V197` 声明 `public.ecos_scenario_run` 停写、产物迁 `ecos_dw.ecos_application_forecast_run`，但 `ScenarioRunService` 仍写旧表 — 本批只做增量列 `run_mode`，不迁移旧表。
3. 蓝图端点挂 `/api/v1/business/scenarios`，场景其余端点挂 `/api/v1/workspace/scenarios` — 保持现状，只增不改。

## 4. 可直接复用

- 响应包络 `ApiResponse`；异常 `WorkspaceExceptionHandler`、`CognitiveErrorAdvice`、`ContextBuildErrorAdvice`
- 审计：`EventBusService` + `KafkaTopics.AUDIT` + `EventTypes.Scenario`
- 缓存：`runtime` 缓存底座（近期 14 类矩阵）
- 权限：`ClearanceInterceptor` + `AnonymousEndpointRegistry` + `ScenarioReadGuard`
- 长任务：`runtime-task` + 前端 `AsyncTaskCenterView`
- 前端：`httpClient`、`cognitiveClient`、`scenarioBlueprintClient`、`useTheme`、`LanguageContext`、六态组件

## 5. 数据库变更需求与兼容风险

| 变更 | 必要性 | 兼容性/回滚 |
|---|---|---|
| 新增 `ecos_cognitive.ecos_cognitive_framework`（V311） | Framework Registry 持久化 | 纯新增表；回滚=停用端点，不删表（IR03） |
| `ecos_scenario_run` 增 `run_mode`、`source_run_id`、`sandbox_lineage` | 沙盘产物可验证标记 + 正式运行拒绝沙盘引用 | `ADD COLUMN IF NOT EXISTS` + 默认 `FORMAL`；回滚=忽略新列 |
| 不新增 `cognitive_context` | 设计红线 | — |
| 不新增认知业务事实表 | 设计红线 | — |

风险：迁移版本门禁 `MigrationVersionUniquenessTest` 基线 310/NEXT 311，新增后须同批升 311/312；Flyway 禁用，脚本仅落单源目录 `docs/50-db_script/02-flyway-migration/gateway/`。

## 6. API / 前端路由 / 权限 / 审计 / 测试现状

- 场景端点 55 条，三前缀并存（workspace/business/workspace-actions）；无任何 scenario 端点进 permitAll（全部需认证）。
- 认知端点约 60 条；路径常量单源 `CognitiveApiPaths`，但 `ContextBuildController.java:49`、`OutcomeEvaluateController.java:38-72` 仍有字面量（既有单源门禁未覆盖，遗留）。
- 前端路由见 §1；六态矩阵组件在位；`ContextBuildPanel` 已对接 `/context/build`；无 framework/strategy/contextDigest UI。
- 测试：workspace 14 个单测 + 静态门禁；cognitive 约 30 个测试类；前端 vitest 与 e2e 工程在位。命令：
  - `mvn -f ecos_backend/pom.xml -pl workspace/workspace-impl -am test`
  - `mvn -f ecos_backend/pom.xml -pl engine/cognitive-engine/cognitive-engine-impl -am test`
  - `cd ecos_frontend && npm run lint && npm test`

## 7. 依赖、风险、阻塞项与建议顺序

- 依赖：Framework Registry → Strategy Selector → Plan Validator → 执行状态机；沙盘隔离独立可并行。
- 阻塞：数据库实跑（本机 PG）需用户授权；E2E 全栈需 7 JAR + 前端同时运行。本批先完成代码与单测，再按授权决定是否实跑迁移。
- 风险：认知框架表属新增治理资产表，需确认不违反“不新增认知业务事实表”（框架定义=过程治理元数据，非业务事实；README §2 明确 Framework Registry 为交付项）。
- 建议顺序：审计/计划 → 框架注册表 → 策略选择+计划校验 → 执行状态机 → 沙盘隔离 → 前端面板 → 回归/E2E → 报告。

## 8. 证据索引

见各表“证据”列；关键源码根：
`ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/`
`ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/`
`ecos_backend/engine/cognitive-engine/cognitive-engine-api/src/main/java/com/chinacreator/gzcm/engine/cognitive/api/`
`ecos_frontend/src/pages/workbench/`、`ecos_frontend/src/pages/scenario/cognition/`、`ecos_frontend/src/services/cognitiveClient.ts`
`docs/50-db_script/02-flyway-migration/gateway/V125/V271/V296~V299`
