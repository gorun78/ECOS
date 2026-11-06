# ECOS 场景工作台与认知引擎 — 实施计划

> 基线：`master` @ `811bd6e5`；审计见同目录《现状审计》。
> 原则：只增不改既有 API/表；复用 workspace/cognitive 既有服务、`ApiResponse`、审计与权限底座；每批先代码+单测，再累积回归。

## 调整说明（相对任务书建议顺序）

任务书建议“先场景核心再认知框架”，但审计发现场景蓝图/绑定/完整度/生命周期 **已 EXISTS**，真正缺口是：
1) 认知框架注册/策略选择/计划校验/执行状态机（MISSING）；
2) 场景沙盘 runMode 断链（CONFLICT/PARTIAL）；
3) 前端完整度与框架面板缺失。

因此执行顺序调整为：`框架注册 → 策略选择/计划校验 → 执行状态机 → 沙盘隔离 → 前端 → 集成回归`。场景核心数据模型不重建，只补隔离与展示，理由：避免机械新增第二套绑定/蓝图模型（违反设计 §3.1/§8.1）。

## 批次 B1：认知框架注册表（Framework Registry）

- 目标：框架定义/版本/注册/查询/发布/启停/适用范围；发布版本不可原地修改。
- 设计章节：认知引擎 §5.1/§5.2/§9.1；验收 CE-03。
- 数据模型：新增 `ecos_cognitive.ecos_cognitive_framework`（V311），唯一键 `(framework_id, version, is_deleted)`；`content_json` + `content_hash`；发布即冻结。
- API（只增）：
  - `GET /api/v1/cognitive/frameworks?problemType=&domain=&status=`
  - `GET /api/v1/cognitive/frameworks/{frameworkId}/{version}`
  - `POST /api/v1/cognitive/frameworks`（注册 DRAFT，幂等：同 id+version 已存在 409）
  - `POST /api/v1/cognitive/frameworks/{frameworkId}/{version}/publish`
  - `POST /api/v1/cognitive/frameworks/{frameworkId}/{version}/status`（DEPRECATED/DISABLED/ENABLED）
- 种子：CF-01-01、CF-04-01、CF-03-03、CF-06-02（V311 内 INSERT，ON CONFLICT DO NOTHING）。
- 测试：版本不可变（发布后 PUT/覆盖 409）、同版本重复注册 409、四框架可查询、停用后不出现在可用列表。
- 回滚：停用端点；表只增不删（IR03）。

## 批次 B2：Strategy Selector + Plan Validator

- 目标：硬约束过滤（状态/适用域/输入/依赖/权限/风险/预算/验证能力）→ 确定性排序 → 计划 DAG → 计划校验。
- 设计章节：认知引擎 §6.1~§6.3；验收 CE-04/CE-05。
- 新增：
  - `FrameworkSelectionService`（过滤 + 原因码 + 排序；评分仅用于排序，输出 `calibrated=false`）
  - `PlanValidator`（环检测、依赖可解析、Schema 引用、能力引用、预算、禁止副作用）
  - `POST /api/v1/cognitive/frameworks/select`
  - `POST /api/v1/cognitive/plans/validate`
- 测试：无权限/缺输入框架高评分也被排除；环路/缺 Schema/无效能力/超预算均拒绝；排除原因码齐全；DAG 无环。
- 回滚：移除新端点；无 DB 变更。

## 批次 B3：认知执行状态机

- 目标：`CREATED→PLANNED→VALIDATING→RUNNING→VERIFYING→SUCCEEDED` + 终态 `NEEDS_INPUT/NEEDS_HUMAN_REVIEW/REJECTED/INVALID_OUTPUT/FAILED/BUDGET_EXCEEDED/CANCELLED`；非法迁移 409；幂等键防重复副作用；业务结论状态与流程状态分离。
- 设计章节：认知引擎 §7.2/§7.3；验收 CE-09。
- 新增：`CognitiveExecutionStateMachine`、`CognitiveExecutionService`（请求态执行记录，含幂等键；审计事件）、`POST /api/v1/cognitive/executions`、`POST /api/v1/cognitive/executions/{id}/transitions`、`GET /api/v1/cognitive/executions/{id}`。
- 约束：不新增执行表；记录为请求态（与 `CognitivePlannerController` 同口径），长任务仍交 `runtime-task`。
- 测试：非法迁移拒绝；SUCCEEDED 需 VERIFYING 且验证通过；重复幂等键返回同记录；超预算/取消终态；业务结论状态独立字段。
- 回滚：停用端点。

## 批次 B4：场景沙盘隔离（workspace）

- 目标：runMode 持久化；沙盘不写正式场景指标；正式运行拒绝沙盘产物；服务端直调不可绕过。
- 设计章节：场景工作台 §5.1/§5.2；验收 SW-05。
- 变更：
  - V312：`ecos_scenario_run` 增 `run_mode`/`source_run_id`/`sandbox_lineage`（`ADD COLUMN IF NOT EXISTS`）。
  - `ScenarioRunService.run` 接收 runMode；沙盘跳过 `updateMetrics`；`insertRun` 落 run_mode。
  - 正式运行校验 `baselineRunId/sourceRunId`：查运行记录，若 `run_mode=SANDBOX` ⇒ 409。
  - 启用既有 `SandboxWriteGuard`：沙盘只允许写 `ecos_scenario_run`/`ecos_scenario_canvas_layout`。
- 测试：沙盘不写 metrics；正式引用沙盘产物 409；沙盘复制不改源 Blueprint；审计含 runMode。
- 回滚：忽略新列；`ADD COLUMN` 保留。

## 批次 B5：场景完整度/框架前端面板

- 目标：完整度与后端单源一致；框架列表/选择/计划校验可操作；loading/empty/403/503/error。
- 设计章节：场景工作台 §2.2/§2.3/§10；README §4 最小纵向闭环。
- 新增：
  - `services/cognitiveFrameworkClient.ts`（frameworks/select/plans validate）
  - `pages/workbench/[scenarioId]/blueprint/FrameworkPanel.tsx`（列表 + 选择 + 计划校验）
  - `CompletenessPanel` 接入 `/completeness`（只展示后端 coverage/missingEdges/islands）
- i18n：`workspace.framework.*` zh-CN/en 同步新增。
- 测试：vitest 客户端错误映射（403/404/503）、面板六态、i18n key 存在性、`tsc --noEmit`。
- 回滚：移除面板挂载，保留新文件不影响既有路由。

## 批次 B6：集成与回归

- 后端：workspace + cognitive 模块全量 `test`；gateway 迁移唯一性门禁同批升 312/313。
- 前端：`npm run lint` + `npm test`（注意本机须 Electron Node24 口径）。
- E2E：优先跑 `ecos-tests --project=api`（需 gateway 等）；全栈 7 JAR + 前端另按授权执行。
- 业务年度预测场景：记录真实可执行部分与阻塞项（数据/服务缺失不得伪绿）。

## 批次 B7：交付文档

- `实施进展.md`、`测试验收报告.md`、`遗留问题.md` 按真实命令输出填写。
- 明确部署/启动/验证与回滚路径。

## 执行状态回写（2026-11-03，以实施进展/测试验收报告为准）

| 批次 | 状态 | 证据锚点 |
|---|---|---|
| B1 框架注册表 | **完成** | V311；`CognitiveFrameworkRegistryServiceTest` 6/6；框架 CRUD/发布/状态端点 |
| B2 Selector + PlanValidator | **完成** | `FrameworkSelectionServiceTest` 4/4、`PlanValidatorTest` 5/5；`/frameworks/select`、`/plans/validate` |
| B3 执行状态机 | **完成（请求态）** | `CognitiveExecutionStateMachineTest` 4/4、`CognitiveExecutionServiceTest` 6/6；持久化仍为进程内（P1-3） |
| B4 沙盘隔离 | **完成（含画布）** | V312；`ScenarioRunSandboxIsolationTest` 4/4、`ScenarioSandboxLayoutGuardTest` 3/3 |
| B5 前端面板 | **完成** | FrameworkPanel/CompletenessPanel/GapSeveritySummary；tsc 通过；4 files/37 tests |
| B6 集成与回归 | **部分** | cognitive 545 / workspace 385 绿；gateway 实启与 health 200；2026-11-04 复验业务端点 403 未复现（P0-3 已关闭，frameworks/users/scenarios 均 200）；年度预测 E2E 仍阻塞于 P0-2 列出的契约/数据缺口 |
| B7 交付文档 | **进行中** | 五份台账持续维护；最终报告待 P0 收口后定稿 |

**计划外追加批次（已执行）**：B8 方法目录、B9 HTTP 契约、B10 五类 Context 源、B11 执行器白名单、B12 画布写入口白名单、B13 缺口严重级查询、B14 执行来源版本、B15 架构守护、B16 缺口前端、B17 迁移回滚演练、B18 启动链路缺陷修复、B19 gateway 实启冒烟、B20 发布门槛非阻断缺口（`openWarnings`/`openSuggestions`）、B21 选择/重规划可追溯（`usedFrameworkVersions`/`planRevision`）。逐项证据见 `实施进展.md`。

**与计划的偏差**：① 审计发现迁移表实际在 `ecos_ai` schema（非 public），V312 已显式限定；② 实启暴露 `planValidator` Bean 冲突与 `ScenarioRunService` 构造器歧义，均在 B18 修复；③ live 业务端点受既有 OPA 无策略基线阻断，E2E 未执行，不伪绿。

## 依赖关系

```
B1 ─→ B2 ─→ B3
B4（独立）
B1/B2 ─→ B5（前端消费）
B1~B5 ─→ B6 ─→ B7
```

## 风险与回滚

- 新表/新列只增不删；脚本幂等。
- 不改既有路径/签名；新增端点默认认证（`/api/v1/cognitive/**` 已在 security 覆盖面内）。
- 若 `MigrationVersionUniquenessTest` 基线冲突：同批改 `MAX_BASELINE_VERSION/NEXT_FREE_VERSION` 并在进展文档留证。
