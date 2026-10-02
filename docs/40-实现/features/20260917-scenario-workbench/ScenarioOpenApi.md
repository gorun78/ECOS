# ScenarioOpenApi — 场景工作台契约面（F07-15 OpenAPI 片段）

> **溯源** · 详细设计-07 场景层-workspace 场景工作台（§五 D / F07-15，REQ-API-05）
> **版本** · 1.0
> **修订** · 2026-10-02
> **上游** · [详细设计-07-场景层-workspace场景工作台-2026-09-29](../../30-设计/详细设计-07-场景层-workspace场景工作台-2026-09-29.md)
> **门禁** · 本清单的 `operationId` 全集由 `workspace-impl` 测试 `OpenApiPathParityTest` 反射守护：每个场景域 HTTP 映射方法必须带 `@Operation(operationId)`，缺项 / 撞名 / 端点漂移即 FAIL。修改端点后须同步本表与测试基线（端点数 39）。

> **口径**：本文件只登记**场景域（scenario-domain）** REST 契约面 —— 即 `com.chinacreator.gzcm.workspace.controller..` 下
> 10 个场景 controller 暴露的端点（workspace 内**非场景域**的 object / knowledge / workbook / objectql controller 不属本册 F07-15 契约面，不在此列）。
> 完整 URL = 类级 `@RequestMapping` 前缀 + 方法级路径；认知读侧四端点为方法级全路径（`/api/v1/business/scenarios/...`，不叠加类前缀）。

## 一、新契约端点（F07 本批确立，P0~P2）

| operationId | HTTP | 路径 | 来源 F | 语义 |
|:--|:--|:--|:--|:--|
| `getScenarioCompleteness` | GET | `/api/v1/workspace/scenarios/{id}/completeness` | F07-02 | 连边覆盖率后端单源（前端只消费不重算） |
| `getScenarioGraph` | GET | `/api/v1/workspace/scenarios/{id}/graph` | F07-02/03 | 完整绑定图 nodes+links+coverage |
| `transitionScenarioStatus` | PATCH | `/api/v1/workspace/scenarios/{id}/status` | F07-07 | 状态机迁移（含允许迁移下发；非法 409） |
| `preValidateScenario` | POST | `/api/v1/workspace/scenarios/{id}/pre-validate` | F07-04 | 心智/沙盘 coverage/绑定三行 PASS/FAIL |
| `listBindingCatalog` | GET | `/api/v1/workspace/binding-catalog` | F07-05 | 六类三层单源词表运行时下发 |
| `listScenarioMinds` | GET | `/api/v1/workspace/scenarios/{id}/minds` | F07-11 era | 心智列表（激活优先） |
| `upsertScenarioMind` | POST | `/api/v1/workspace/scenarios/{id}/minds` | F07-11 era | 新建/更新心智（upsert） |
| `updateScenarioMind` | PATCH | `/api/v1/workspace/scenarios/{id}/minds/{mindId}` | F07-11 era | 部分更新（PATCH 语义） |
| `deleteScenarioMind` | DELETE | `/api/v1/workspace/scenarios/{id}/minds/{mindId}` | F07-11 era | 逻辑删心智 |
| `runScenario` | POST | `/api/v1/workspace/scenarios/{id}/runs` | F07-08 | 发起运行（body 可带 run_mode/baselineRunId） |
| `listScenarioRuns` | GET | `/api/v1/workspace/scenarios/{id}/runs` | F07-08 | 运行历史（`?limit=`） |
| `listScenarios` | GET | `/api/v1/workspace/scenarios` | F07-14 | 场景列表（`?purpose=forecast` → 只回 ACTIVE∧无孤岛） |

## 二、认知读侧四端点（F07-12 / REQ-API-02，只读 GET，503 原样穿透）

| operationId | HTTP | 路径 | 语义 |
|:--|:--|:--|:--|
| `getCognitionDetect` | GET | `/api/v1/business/scenarios/{id}/cognition/detect` | 异常检测读侧 |
| `getOperationEval` | GET | `/api/v1/business/scenarios/{id}/cognition/operation-eval` | 经营评估 |
| `listHypotheses` | GET | `/api/v1/business/scenarios/{id}/cognition/hypotheses` | 假设集（含 confidence/evidence） |
| `listBeliefs` | GET | `/api/v1/business/scenarios/{id}/cognition/beliefs` | 信念状态 |

> 引擎不可用 → `CognitiveEngineUnavailableException` → 真实 HTTP 503（A3 红线，禁 200 空 body）。
> **两形态并存**：旧 `POST /api/v1/workspace/scenarios/{id}/cognitive/{diagnose|forecast|simulate|policy}`
> （触发型，`cognitiveDiagnose`/`cognitiveForecast`/`cognitiveSimulate`/`cognitivePolicy`）与上表读侧型语义不同，**均保留不动**（API 只增不改）。

## 三、既有 CRUD / 绑定 / 选项 / 沙盘（既有契约，本批仅补 operationId，未改签名）

| operationId | HTTP | 路径 |
|:--|:--|:--|
| `getScenario` | GET | `/api/v1/workspace/scenarios/{id}` |
| `createScenario` | POST | `/api/v1/workspace/scenarios` |
| `updateScenario` | PUT | `/api/v1/workspace/scenarios/{id}` |
| `deleteScenario` | DELETE | `/api/v1/workspace/scenarios/{id}` |
| `listScenarioBindings` | GET | `/api/v1/workspace/scenarios/{id}/bindings` |
| `listBindingLinks` | GET | `/api/v1/workspace/scenarios/{id}/binding-links` |
| `saveBindingLink` | POST | `/api/v1/workspace/scenarios/{id}/binding-links` |
| `deleteBindingLink` | DELETE | `/api/v1/workspace/scenarios/{id}/binding-links/{linkId}` |
| `listAvailableDatasets` | GET | `/api/v1/workspace/scenarios/available/datasets` |
| `listAvailableObjects` | GET | `/api/v1/workspace/scenarios/available/objects` |
| `listAvailableKnowledge` | GET | `/api/v1/workspace/scenarios/available/knowledge` |
| `listAvailableAgents` | GET | `/api/v1/workspace/scenarios/available/agents` |
| `listAvailableSecurity` | GET | `/api/v1/workspace/scenarios/available/security` |
| `listAvailableInterfaces` | GET | `/api/v1/workspace/scenarios/available/interfaces` |
| `getSandboxLayout` | GET | `/api/v1/workspace/scenarios/{id}/sandbox/layout` |
| `saveSandboxLayout` | POST | `/api/v1/workspace/scenarios/{id}/sandbox/layout` |
| `getScenarioMindModel` | GET | `/api/v1/workspace/scenarios/{id}/mind-model` |
| `saveScenarioMindModel` | POST | `/api/v1/workspace/scenarios/{id}/mind-model` |
| `deleteScenarioMindModel` | DELETE | `/api/v1/workspace/scenarios/{id}/mind-model` |

## 四、状态码契约（F07-13 联动）

所有端点经 `WorkspaceExceptionHandler`（`basePackages=com.chinacreator.gzcm.workspace`）落真实 HTTP：
`400`（动作五必填 / SANDBOX_REF / 非法入参）、`403`（权限拒绝）、`404`（场景不存在）、
`409`（孤岛 / 非法迁移 / 沙盘乐观锁）、`503`（认知引擎 / 跨服务不可达，原样穿透）。
内部异常原文只进日志 + traceId，**不入体**（A3 红线）。

## 五、未入本表的场景疑似端点（诚实登记，不冒充实装）

- **`/forecast-scope`**（F07-14 范围预填）：设计要点 2 要求，但运行体依赖分册 09 forecast-run 服务（跨竖切片），本窗未接，不加路由以免落不可达桩。路由需预测运行服务落地后补 `forecastScope` 端点并进本表 + `OpenApiPathParityTest` 基线。
- **`POST /api/v1/cognitive/action-proposals`** 动作闭环写侧（F07-10）：本批仅落**写侧校验骨架**（`ActionProposalValidator`/`ActionProposalDTO`/`BaselineReferenceGuard#assertForecastRunIsFormal`，单测绿）；actions CRUD / approve / decision-record 读写的 HTTP 端点为分册 09 断链件，未建路由，故不入本表（需 F07-01 承流可达后端到端验证）。
