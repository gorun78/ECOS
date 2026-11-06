# ECOS 场景工作台与认知引擎 — 测试验收报告

> 日期：2026-11-03
> 口径：只登记**实际运行**的命令与输出；未运行的项标 `未执行`，不用推断代替证据。

## 1. 测试命令总表

| # | 命令 | 范围 | 结果 | 输出/证据 |
|---|---|---|---|---|
| 1 | `mvn -o -f ecos_backend/pom.xml -pl engine/cognitive-engine/cognitive-engine-impl -am compile -DskipTests` | 认知 api/impl 编译 | **PASS** | 编译无错误 |
| 2 | `mvn -o ... -pl engine/cognitive-engine/cognitive-engine-impl test` | 认知模块全量单测（最终，执行来源版本 + 白名单 + 架构守护后回归） | **PASS** | `Tests run: 543, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（2026-11-03 20:57 实跑） |
| 3 | `mvn -o ... -pl workspace/workspace-impl test` | 场景层全量单测（画布白名单 + 缺口查询后回归） | **PASS** | `Tests run: 381, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS` |
| 4 | `mvn -o ... -pl gateway test -Dtest=MigrationVersionUniquenessTest` | 迁移版本唯一/顺延门 | **PASS** | `Tests run: 5, Failures: 0` |
| 5 | `cd ecos_frontend && npm run lint` | 前端 TS 全量类型检查 | **PASS** | `tsc --noEmit` 无输出 |
| 6 | `npx vitest run --environment=node src/services/__tests__/cognitiveFrameworkClient.test.ts "src/pages/workbench/[scenarioId]/blueprint/__tests__/frameworkI18n.spec.ts"` | 新前端通道/i18n | **PASS** | `2 files / 6 tests passed` |
| 7 | `npx vitest run --environment=node .../sixStates.matrix.guard.spec.ts .../i18nKeys.exist.spec.ts src/__tests__/no-hardcoded-colors.test.ts` | 既有结构门禁回归 | **PASS** | `3 files / 36 tests passed` |
| 8 | 全栈 E2E（7 JAR + 前端 + Playwright） | 端到端业务 | **未执行** | 未拉起全栈；见遗留问题 |
| 9 | V311/V312 对 PG 实跑（落库） | 数据迁移 | **未执行** | 脚本未落库；已完成**事务回滚演练**（见下第 25/26 行） |
| 10 | `mvn ... test -Dtest='*ContextSourceReaderTest,*ContextBuilderServiceTest,*FrameworkSelectionServiceTest,*PlanValidatorTest,*CognitiveExecutionServiceTest,*CognitiveExecutionStateMachineTest,*CognitiveFrameworkRegistryServiceTest'` | 上下文/框架定向回归 | **PASS** | `Tests run: 47, Failures: 0`（含 ContextBuilder 14、DataEngine 7、K/I 新增 4） |
| 11 | `mvn ... -pl workspace/workspace-impl test -Dtest=CompletenessReportTest` | 完整度 POJO/阻断分类 | **PASS** | `Tests run: 7, Failures: 0` |
| 12 | `npm run lint`（完整度面板后） | 前端类型 | **PASS** | `tsc --noEmit` 无输出 |
| 13 | `mvn ... test -Dtest='CognitiveMethodCatalogServiceTest,CognitiveFrameworkContractTest'` | 方法目录 + 新端点 HTTP 契约 | **PASS** | `Tests run: 7, Failures: 0`（方法 3 + 契约 4） |
| 14 | `mvn ... -pl workspace/workspace-impl test`（完整度阻断分类后） | 场景层全量 | **PASS** | `BUILD SUCCESS` |
| 15 | `mvn ... test -Dtest=CapabilityContextSourceReaderTest,CognitiveAssetContextSourceReaderTest` | CAPABILITY / C 资产读取面 | **PASS** | `Tests run: 6, Failures: 0`（各 3） |
| 16 | `mvn ... test -Dtest=CognitiveScenarioIntegrationTest` | 场景→选择→计划→执行链路 | **PASS** | `Tests run: 1, Failures: 0` |
| 17 | `mvn ... test -Dtest=CognitiveFrameworkRegistryServiceTest` | 框架执行器白名单（P1-8） | **PASS** | `Tests run: 6, Failures: 0`（含 `unknownExecutorTypeIsRejected`） |
| 18 | `mvn ... -pl workspace/workspace-impl test -Dtest=ScenarioSandboxLayoutGuardTest` | 画布写入口白名单（P1-7） | **PASS** | `Tests run: 3, Failures: 0` |
| 19 | `mvn ... test -Dtest=ScenarioBlueprintGapsQueryTest` | 缺口严重级服务端分类（P1-4） | **PASS** | `Tests run: 3, Failures: 0` |
| 20 | `mvn ... test -Dtest=ScenarioBlueprintGapsContractTest` | `GET {id}/gaps` 路径/包络/400 | **PASS** | `Tests run: 2, Failures: 0` |
| 21 | `mvn ... test -Dtest=CognitiveExecutionServiceTest` | 执行记录来源资产版本（可追溯） | **PASS** | `Tests run: 6, Failures: 0`（含来源版本保存/缺版本拒绝） |
| 22 | `mvn ... test -Dtest=ArchitectureTest` | 架构守护（引擎边界 + 禁私有调度器/线程池） | **PASS** | `Tests run: 6, Failures: 0` |
| 23 | `cd ecos_frontend && npm run lint`（缺口摘要后） | 前端类型 | **PASS** | `tsc --noEmit` 无输出 |
| 24 | `npx vitest run --environment=node scenarioBlueprintGapsClient.test.ts sixStates.matrix.guard.spec.ts i18n-literal-keys-global.test.ts no-hardcoded-colors.test.ts` | 缺口 client + 结构门禁回归 | **PASS** | `4 files / 37 tests passed` |
| 25 | `docker exec -i ecos-postgres psql -v ON_ERROR_STOP=1 -f <BEGIN+V311+V312+ROLLBACK>` | 迁移脚本可执行性/兼容性（事务回滚演练） | **PASS** | 输出：`CREATE TABLE/INDEX/COMMENT/INSERT 0 4/ALTER TABLE ×3/CREATE INDEX/COMMENT ×3/ROLLBACK`，无报错 |
| 26 | 回滚后只读复核 | 演练未落库 | **PASS** | `framework=ABSENT`、`ecos_ai.ecos_scenario_run.run_mode` 列计数 `0` |
| 27 | `mvn ... -pl gateway -am install -DskipTests` | 全链路打包（含新增 Bean） | **PASS** | `BUILD SUCCESS` |
| 28 | `start-backend.ps1 -Modules gateway` | 真实启动 | **PASS** | `Started GatewayApplication in 77.558 seconds`，:8080 监听 |
| 29 | live `POST /api/v1/auth/login` + `GET /api/v1/engine/cognitive/health` | 启动后 API 冒烟 | **PASS** | 登录 `code=0`；健康 `200 {"status":"UP","components":{"db":"UP","engine":"RUNNING"}}` |
| 30 | live 业务读端点（methods/scenarios/knowledge-bases） | 安全前置 | **阻塞（既有基线）** | 均 `403`；排除 JWT 密钥/密级/OPA/Clearance（密钥匹配、admin L5、无 JWT/Clearance 告警、Clearance 会回 ApiResponse）⇒ 403 在 Spring Security 鉴权链层，需 live debug |
| 31 | `mvn ... -pl workspace/workspace-impl test`（启动修复后回归） | 场景层全量 | **PASS** | `Tests run: 381, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS` |
| 32 | `mvn ... -pl engine/cognitive-engine/cognitive-engine-impl test`（Bean 消歧后回归） | 认知全量 | **PASS** | `Tests run: 543, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| 33 | `mvn ... -pl workspace/workspace-impl -Dtest=ScenarioBlueprintValidatorTest test` | 发布门槛非阻断分类 | **PASS** | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| 34 | `mvn ... -pl workspace/workspace-impl test`（非阻断分类后全量回归） | 场景层全量 | **PASS** | `Tests run: 384, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`（3 skip 为既有） |
| 35 | `cd ecos_frontend && npm run lint`（非阻断明细接入后） | 前端类型 | **PASS** | `tsc --noEmit` 无输出 |
| 36 | `npx vitest run --environment=node i18n-literal-keys-global.test.ts sixStates.matrix.guard.spec.ts no-hardcoded-colors.test.ts scenarioBlueprintGapsClient.test.ts` | i18n/六态/零字面色/缺口 client 回归 | **PASS** | `Test Files 4 passed / Tests 37 passed` |
| 37 | `mvn ... -pl engine/cognitive-engine/cognitive-engine-impl -Dtest=FrameworkSelectionServiceTest test` | 选中版本/原因 + 重规划痕迹 | **PASS** | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| 38 | `mvn ... -pl engine/cognitive-engine/cognitive-engine-impl test`（B21 后全量） | 认知全量回归 | **PASS** | `Tests run: 543, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| 39 | `cd ecos_frontend && npm run lint` + `npx vitest run --environment=node cognitiveFrameworkClient.test.ts frameworkI18n.spec.ts i18n-literal-keys-global.test.ts no-hardcoded-colors.test.ts`（B21 前端接入后） | 前端类型 + 框架 client/i18n/零字面色 | **PASS** | `tsc --noEmit` 无输出；`Test Files 4 passed / Tests 13 passed` |
| 41 | `docker exec ecos-postgres psql -U postgres -d sys_man -f V311.sql && -f V312.sql`（2026-11-04 授权落库） | 迁移永久执行 | **PASS** | `CREATE TABLE/INDEX/COMMENT/INSERT 0 4/ALTER ×3`；复核：表存在、种子 4、3 列、3 索引、`run_mode` 默认 `FORMAL` |
| 42 | `mvn ... -pl gateway -Dtest=MigrationVersionUniquenessTest test` | 迁移版本唯一/顺延门 | **PASS** | `Tests run: 5, Failures: 0, Errors: 0` / `BUILD SUCCESS` |
| 43 | live login + `GET /api/v1/cognitive/frameworks` + `POST .../publish` + `GET /api/v1/system/users` | 迁移表 API 读写 / live 鉴权 | **PASS** | 登录 `code=0`；frameworks 返回 4 条 DRAFT；publish `CF-01-01@1.0.0` 200 且 PUBLISHED 列表可见；users 200（此前 403 未复现） |
| 44 | `mvn ... -Dtest=CognitiveContextOutcomeContractTest test` + 认知全量 | Context/Outcome HTTP 契约 + 回归 | **PASS** | 契约 `Tests run: 2, Failures: 0`；全量 `Tests run: 545, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| 45 | live（gateway :8080，admin token + `X-ECOS-USER/X-ECOS-TENANT`）：`GET /api/v1/business/scenarios/sc_ent2_group_month/blueprints/bp_ent2_sc_ent2_group_month/validate` 与同名 `/gaps` | 蓝图 `GET {id}/validate` 只读契约（历史自由文本 gaps 不得 400） | **PASS** | validate `HTTP=200`、`code=0`、`passed=false`、`reasons=["…fail-closed…"]`、`details.invalidContent` 含原始解析错误；gaps `HTTP=200`、`counts` 全 0。修正前同一请求为 `400 BLUEPRINT_CONTENT_INVALID` |
| 46 | live `POST /api/v1/cognitive/frameworks/select`（problemType=metric_deviation / domain=enterprise_operations / presentInputs 2 项 / capabilities=COGNITIVE_DIAGNOSE / riskLevel=MEDIUM + APPROVED） | 框架选择 live：硬过滤 + 序号排序 + 未校准语义 | **PASS** | `HTTP=200`、`code=0`；`selectedFrameworks=[]`、`excluded[{CF-01-01, reasons=[PROBLEM_TYPE_MISMATCH, MISSING_INPUT:goal_statement_present]}]`、`selectionTrace.ranking=DETERMINISTIC_ORDINAL_ONLY`、`probabilitySemantics=NONE`、`status=NEEDS_INPUT_OR_HUMAN`、`usedFrameworkVersions=[]` |
| 47 | `cd ecos_frontend && npm run lint`（2026-11-04 收口复跑） | 前端类型检查回归 | **PASS** | `tsc --noEmit` 无输出（零错误） |
| 48 | live `POST /api/v1/business/scenarios/sc_ent2_group_month/blueprints/bp_ent2_sc_ent2_group_month/transition` body=`{target:"RELEASED"}` | 异常场景「非法状态转换被拒绝」+ 拒绝后不产生副作用 | **PASS** |
| 49 | 修复 `NotFoundException` 未映射（`WorkspaceExceptionHandler` 新增 404 处理器）后：live `GET /api/v1/workspace/scenarios/sc_does_not_exist` + `mvn -pl workspace/workspace-impl test` | 异常场景「引用不存在的资源」应 404（原为 500） | **PASS** | 修复前 `500 INTERNAL`（日志 `NotFoundException: SCEN-404` 落兜底）；修复后 `HTTP=404`、`errorCode=NOT_FOUND`、`message="SCEN-404: 场景不存在: sc_does_not_exist"`；既有场景仍 `200`；全量 `Tests run: 385, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS` |
| 50 | 知识同步 500 修复：V313 加宽 4 列（dev 库实跑）→ live `POST /api/v1/knowledge/sync/object/{ontologyId}` → `MigrationVersionUniquenessTest` | 「知识同步可执行」（原 `ECOS-KB-050 value too long varchar(20)`） | **部分通过** | 迁移 4×`ALTER TABLE` 成功；同步由 **500 → 非 500**（`started`，日志转 `读取本体快照失败`）；门禁 `Tests run: 5, Failures: 0` / `BUILD SUCCESS`。**仍未 present**：`ecos_knowledge.ecos_kb_ontology_snapshot` 与 `ecos_ontology.kb_ontology_snapshot` 均 0 行，`lastSyncTime` 全 null |
| 51 | `mvn -o test -Dtest=OntologyVersionContractDaoTest` + ontology 模块全量 `mvn -o test`；live `POST /ecos/ontologies/ont_entmgmt/versions` → `.../publish` | 本体版本发布（快照生成的前置）原为 500 | **PASS** | 新测试 `6/0/0`；模块全量 `288/0/0`；live 创建 `200 ver5001/1.0.0 Draft`、发布 `200 status=Published`；修复前为 `500 ECOS-DATA-031`（日志 `column "created_at" does not exist`） |
| 52 | 快照腿切 V179 + `V314` 实跑（8 列/3 索引）→ live 发布 `ver5002/1.0.1` → `POST /knowledge/sync/object/ont_entmgmt` → `GET /knowledge/sync/status` | EXTRACTION 数据前置（快照落盘、同步可跑） | **部分通过** | `ecos_kb_ontology_snapshot` 落 1 行 `ont_entmgmt@1.0.1 / hash=c063657a… / admin / is_deleted=0`，该次发布**无 FAILED 行**；`sync/object` → `200 started`（不再「读取本体快照失败」）。**下游仍阻**：job `tg-obj-1793776670109` `FAILED / ECOS-DATA-041 …/engine/data/layers/CURATED (403)`（同端点带 admin Token 为 200）；`lastSyncTime` 仍 null |
| 53 | `cd ecos_backend/engine/kb-engine/kb-engine-impl && mvn -o test`（快照腿 4 个 DAO 改动后） | kb 引擎回归 | **PASS** | `Tests run: 343, Failures: 0, Errors: 0, Skipped: 2` / `BUILD SUCCESS` | `HTTP=409`、`errorCode=ILLEGAL_TRANSITION`、message 列合法出边 `[VALIDATING, GAP_ANALYSIS, NEEDS_CLARIFICATION, BLOCKED, DRAFT]`；随后 `GET` 复核 `status=BLUEPRINT_READY` **未变**（无副作用） |
| 40 | `powershell -NoProfile -ExecutionPolicy Bypass -File _win_tasks/db-migration-lint.ps1` | 迁移脚本静态门禁（辅助证据） | **PASS（辅助）** | `PASS: 0 项 FAIL, 8 项 WARN`；脚本输出扫描根为 `ecos_backend`，**未证明** V311/V312 在扫描集内 ⇒ 不替代迁移实跑；另人工核对：V311 `ecos_cognitive.` 限定 / `ecos_` 前缀 / JSONB 全 `_json` 后缀 / 布尔 `SMALLINT 0/1` / 七基线列齐；V312 `ecos_ai.ecos_scenario_run` 限定、只 `ADD COLUMN`/`CREATE INDEX` |

## 2. 新增/变更测试明细

### 2.1 认知框架注册表 `CognitiveFrameworkRegistryServiceTest`（6）
- DRAFT 强制 + contentHash 生成；
- 同版本重复注册 → `ECOS-COG-409S` 且不落库；
- 已发布再发布 → `ECOS-COG-409R` 且不写库；
- 契约不全发布 → `ECOS-COG-422R`；
- PUBLISHED→VALIDATING 非法迁移 → 409；
- `executorType` 不在白名单 → `E400_INVALID_PARAM`（禁任意代码容器）。

### 2.2 策略选择 `FrameworkSelectionServiceTest`（4）
- 缺前置输入的候选即便更匹配也被排除，原因码 `MISSING_INPUT` 可追溯；选中项 `calibrated=false`、`scoreKind=ORDINAL_ONLY`；
- 未授权 → 不选任何框架、状态 `NEEDS_INPUT_OR_HUMAN`、排除原因 `PERMISSION_DENIED`；
- `maxSteps` 预算 → `BUDGET_EXCEEDED`；
- 计划 DAG 校验 passed/acyclic。

### 2.3 计划校验 `PlanValidatorTest`（5）
合法计划通过；环 → `DAG_CYCLE`；缺 Schema/能力/预算/副作用 → 四种阻断码；未知依赖 + 未授权 → 双阻断；空计划拒绝。

### 2.4 执行状态机 `CognitiveExecutionStateMachineTest`（4）+ `CognitiveExecutionServiceTest`（4）
合法链路、非法跳转/终态拒绝、SUCCEEDED 双验证门、终态集合；幂等键返回同记录、失败/取消可追踪、业务结论状态独立、关联键齐全。

### 2.5 沙盘隔离 `ScenarioRunSandboxIsolationTest`（4）
- SANDBOX 不调用 `updateMetrics`，写 `run_mode=SANDBOX`/`lineage=SANDBOX`；
- FORMAL 正常回写指标、`lineage=NONE`；
- FORMAL 引 SANDBOX 基线 → `SandboxReferenceForbiddenException`；
- 写白名单只含 run/画布，不含正式业务表。

### 2.6 前端（6）
- 客户端只走 `/api/v1/cognitive/frameworks`、`/select`、`/plans/validate`；503/403/404/traceId 映射为 `CognitiveFrameworkApiError`；
- FrameworkPanel 24 个 i18n key zh/en 齐全；组件代码无硬编码中文字符串（注释除外）。

### 2.7 发布门槛非阻断分类 `ScenarioBlueprintValidatorTest`（3）
- 仅 WARNING/SUGGESTION 未关闭 → `passed=true`，`details.openWarnings`/`openSuggestions` 仍可见（通过不再丢明细）；
- `RESOLVED`/`ACCEPTED_RISK` 不计入未关闭；
- 存在未关闭 BLOCKER → `passed=false` 并给出原因，同时保留非阻断明细。
- 前端 `BlueprintValidatePanel` 增两行只读明细并标注“不阻断”，i18n zh/en 键齐全（结构门禁 4 文件/37 用例通过，`tsc --noEmit` 无输出）。

### 2.8 选中版本与重规划痕迹 `FrameworkSelectionServiceTest`（4）
- `selectedFrameworks[]` 携带 `selectionReason=HARD_FILTERS_PASSED`；顶层 `usedFrameworkVersions[]` 明确 `frameworkId/version/selectionReason`；
- `PLANNED` 不带 `planRevision`；校验失败时 `planRevision.decision=REPLAN_OR_REJECT`、`reason=PLAN_VALIDATION_FAILED`、`issues` 与已用版本齐全。
- 前端 `FrameworkPanel` 已展示 `usedFrameworkVersions` 与 `planRevision`（含 issues），i18n zh/en 键齐全；`tsc --noEmit` 无输出，框架 client + i18n + 零字面色 `4 files / 13 tests passed`。

### 2.9 Context/Outcome HTTP 契约 `CognitiveContextOutcomeContractTest`（2）
- `POST /api/v1/cognitive/context/build` 与 `POST /api/v1/cognitive/outcomes/evaluate` 路径保持，成功返回 `code=0` 且含 `data`；
- Service 以 mock 隔离，仅验路径/包络；JSON 需显式 `Accept: application/json`（其余框架默认内容协商会回 XML）。

### 2.10 蓝图 `GET {id}/validate` 历史自由文本 fail-closed（`ScenarioBlueprintValidatorTest` 4）
- 端点契约（Controller 注释）：**只读，200 + passed 明细**；此前历史蓝图 `gaps` 为自由文本时，`arrayNodes` 抛 `BlueprintContentInvalidException` 直接冒泡为 `400 BLUEPRINT_CONTENT_INVALID`，违反契约。
- 修正：`validateForRelease` 捕获该异常 → `passed=false` + reason 前缀「按 fail-closed 判为未通过」+ `details.invalidContent` 保留原始解析错误；**不判通过、不静默吞**。
- 新增 `unstructuredGapsFailClosedWithDetailInsteadOfThrowing`：自由文本 gaps → `assertFalse(passed)`、reason 含 `fail-closed`、details 含 `invalidContent`。
- 全量回归 `Tests run: 385, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`；live 证据见 §1 第 45 行。

## 3. `实施任务与验收清单.md` 验收结果

### 3.1 场景工作台

| 用例 | 状态 | 证据/说明 |
|---|---|---|
| SW-01 业务语言入口 | 未执行（存量 UI） | L1/L2 页面存在；本次未起前端实点 |
| SW-02 完整度单源 | 通过（后端单测 + 前端只读面板） | `ScenarioCompletenessServiceTest` 在 workspace 全量绿；`CompletenessPanel` 已挂蓝图 `completeness` Tab（只渲染后端字段）；`GapSeveritySummary` 接入 `GET {id}/gaps` 服务端分类；发布门槛 `validateForRelease` 分级输出 `openBlockerGaps`/`openWarnings`/`openSuggestions`（仅 BLOCKER 阻断，`ScenarioBlueprintValidatorTest` 3/3） |
| SW-03 孤岛阻断 | 通过（单测） | `IslandBindingGuardTest` 4/4、`ScenarioStatusMachineTest` 4/4（DRAFT→ACTIVE 要求 islands 空 + verdict≠EMPTY + 认知可达） |
| SW-04 E1-E4 语义 | 通过（既有单测）/ live 未执行 | 认知全量含 E1-E4 契约/错误矩阵测试；未跑活栈 |
| SW-05 沙盘隔离 | **通过（新增单测）** | `ScenarioRunSandboxIsolationTest` 4/4；画布写入口 `ScenarioSandboxLayoutGuardTest` 3/3 |
| SW-06 Blueprint 版本不可变 | 通过（单测） | `ScenarioBlueprintServiceTest` 10/10、`BlueprintStatusMachineTest` |
| SW-07 安全失败关闭 | 未执行（既有实现） | 未模拟 PDP 下线实跑 |
| SW-08 预测金额可追溯 | 未执行（E2E） | 既有 forecast-run API 存在；未跑业务场景 |
| SW-09 运行与取消 | 未执行 | runtime-task/AsyncTaskCenterView 未起 |
| SW-10 无伪成功 | 通过（静态/结构门） | 六态矩阵结构门（含新增面板）在 `4 files / 37 tests` 回归中通过；未执行项均标未执行 |

### 3.2 认知引擎

| 用例 | 状态 | 证据/说明 |
|---|---|---|
| CE-01 Context 来源追溯/digest | 通过（既有单测） | 认知全量含 `ContextBuilderService`/`ContextDigestCalculator` 测试 |
| CE-02 不新增 context 表 | **通过** | V311 仅框架治理表、V312 仅加列；白名单门登记后 `CognitiveIronLawsTest` 4/4 绿 |
| CE-03 框架版本不可变 | **通过（新增单测）** | `CognitiveFrameworkRegistryServiceTest` 6/6 |
| CE-04 硬约束优先 | **通过（新增单测）** | `FrameworkSelectionServiceTest` 4/4 |
| CE-05 计划校验 | **通过（新增单测）** | `PlanValidatorTest` 5/5 |
| CE-06 证据冲突保留 | 通过（既有单测） | 证据/假设/信念既有测试在全量中绿 |
| CE-07 反因果过度断言 | 通过（既有门禁） | `CognitiveIronLawsTest`/`CognitiveNoLlmNumbersTest` 等绿 |
| CE-08 正式金额来源 | 通过（既有门禁） | 预测 run/数值门禁测试绿；未跑真实预测 |
| CE-09 运行时停止 | **部分通过** | 状态机超预算/取消/失败终态单测通过；未接真实执行器 |
| CE-10 E1-E4 兼容 | 通过（既有单测） | 全量认知测试绿；未跑 live |
| CE-11 受控反馈 | 通过（既有单测） | `OutcomeEvaluateServiceTest` 9/9 |
| CE-12 安全 fail-closed | 通过（既有单测） | `CognitiveAuditFailClosedTest` 在全量中绿 |

## 3.3 按 `实施任务与验收清单.md` 阶段汇总

| 阶段 | 状态 | 证据 / 缺口 |
|---|---|---|
| 0 仓库与契约审计 | 通过 | `现状审计.md` 已落盘，映射附类名/路径/测试证据；基线单测已实跑（本报告 1~3 行） |
| 1 契约与安全守护 | 部分通过 | 关联键（traceId/scenarioId/blueprintVersion/contextDigest/runId）与架构守护有单测（`ArchitectureTest` 6/6、`CognitiveIronLawsTest`）；**PDP 下线 live 负向未执行**（P0-4） |
| 2 场景工作台纵向闭环 | 部分通过 | SW-02/03/05/06/10 有单测/结构门证据；SW-07/08/09 未执行（需 live/数据）；完整度服务端权威、孤岛阻断、sandbox 拒绝正式引用均有用例 |
| 3 Context Builder 与契约 | 部分通过 | 五类源读取面、digest、gap、来源版本单测通过；**`ContextSecurityTrimmer` 无生产实现**，真实宿主 required 源恒 403（P1-9） |
| 4 Framework Registry 与策略选择 | 通过（单测层 + live 选择） | 版本不可变、硬过滤优先、排除原因码、计划 DAG/校验、选择版本与重规划痕迹（B21）均有实测；live `POST /frameworks/select` 200：仅 PUBLISHED 进候选（DRAFT 被过滤）、排除原因码回传、`ranking=DETERMINISTIC_ORDINAL_ONLY`、`probabilitySemantics=NONE`、无输入时 `NEEDS_INPUT_OR_HUMAN`（本报告 §1 第 46 行）。真实执行仍归 W Runtime（未私建 Loop，架构测试守护） |
| 5 年度经营预测 E2E | **阻塞** | 10 项业务验证无完整 live 证据，不伪造通过。2026-11-04 新增 live 取证见 §3.4：目标场景完整度 `PARTIAL`（1/3，两条 `BLOCKER` 边缺失），且该场景 `ecos_scenario_run` 无任何运行记录 |

> 口径：阶段 1/2/3 的“部分通过”仅表示所列子项已实跑；其未覆盖部分已在 P0/P1 登记，不计为通过。

### 3.4 年度经营预测 E2E live 取证（2026-11-04，判定：阻塞）

| 观测 | 实测结果 |
|---|---|
| 场景读取（workspace:18090 + Bearer + `X-ECOS-USER/ROLE/TENANT/DOMAIN`） | `GET /api/v1/workspace/scenarios/sc_ent2_group_month` → `200`；名称「集团月度经营分析（2026 年度）」、`status=ACTIVE` |
| 已绑定资产 | `bindings`：datasets `res_entmgmt_cur_attribution`、objectTypes `0e4e92d5…`、knowledgeBases `cf94fecc…`、aiAgents `ag_ent2_cognitive`、securityPolicies `abac_ent2_exec_read`、interfaces `if_ent2_forecast_list` |
| 链路完整性（后端权威） | `GET .../completeness` → `coverage=0.3333`、`verdict=PARTIAL`；缺失边均为阻断：① `EXTRACTION`「KNOWLEDGE_BASE 未引用任何 OBJECT_TYPE（契约未通过）」② `COGNITION`「AI_AGENT 未引用任何 KNOWLEDGE_BASE（契约未通过）」 |
| 连边存在性（订正） | `GET .../graph` 返回 6 节点 + 5 边，含 `bsl_ent2_extraction_*`(obj→kb) 与 `bsl_ent2_cognition_*`(kb→ag) ⇒ **缺的不是连边**。`probeErrors` 仅 `COGNITION` 一条；`EXTRACTION` 无探测错误，卡在 `ContractSnapshot.isVerified()` 要求「`contractCount>0` **且** `lastVerifiedAt` 非空」，实测 kb `/knowledge/sync/status` 的 `syncedCount` 合计 28 但 `lastSyncTime` 全 `null` |
| 知识同步实跑（为补 `lastSyncTime`） | ① 起始：`POST /knowledge/sync/trigger` 与 `/sync/object/{id}` 均落 `failed`「读取本体快照失败」；`bc129d24…` 曾 500 `value too long varchar(20)`（已由 V313 修复）。② **B28**：本体版本发布原 500（`OntologyVersionDao` 读写 `created_at`/`snapshot::jsonb`），已修 → live `create/publish 200`。③ **B29**：快照腿切 V179 `ecos_kb_ontology_snapshot` + V314 补 8 溯源列 → `ont_entmgmt@1.0.1` 快照落盘（hash `c063657a…`），该次发布无 FAILED 行；`POST /knowledge/sync/object/ont_entmgmt` → `200 started`。④ **仍阻**：抽取 job `tg-obj-…` `FAILED / ECOS-DATA-041 …/data/layers/CURATED (403)`（同端点带 admin Token 200，属服务间凭据缺口 P1-12）；`sync/status.lastSyncTime` 仍全 `null`（P1-13）⇒ EXTRACTION 仍无法 present |
| 绑边接口负向校验 | 以资源 id（非 `sb_*` 绑定 id）调用 `POST .../binding-links` → `LINK-400: 绑定 … 不属于场景 sc_ent2_group_month`（服务端正确拒绝跨场景绑定；非缺陷） |
| 已有执行记录 | `ecos_ai.ecos_scenario_run` 全表 **1 行**：`sc_entmgmt / FORMAL / SUCCEEDED_DEGRADED`；目标场景 `sc_ent2_group_month` **0 行**；`SANDBOX` 运行 **0 行** |
| 网关面差异（部署事实） | gateway :8080 承载 `/api/v1/business/scenarios/**`（blueprints 200），**不承载** `/api/v1/workspace/scenarios/**`（404）；场景族须直连 workspace:18090 并携带 Bearer + 四个信任头 |
| 正式口径读取（E2E 第 1~3 项前置） | `GET /api/v1/workspace/calibers` → **403** `PERMISSION_DENIED / permission=scenario:read:caliber_definition / reason=RLS_NO_POLICY_MATCHED`。库内 `ecos_control.ecos_rls_policy` 共 31 条，`policy_name/filter_expr/bindings_json` 均无 `caliber` 命中 ⇒ 非代码缺陷，是**安全策略数据缺口**（fail-closed 表现正确，不得绕过） |
| `COGNITION` 边契约载体 | `HttpContractProbeFacade` 对 `COGNITION` 直接抛 `IllegalStateException("COGNITION 边无契约载体：智能体注册表未持有知识库引用（需求侧缺口，非探测故障）")` ⇒ 该边在任何数据状态下都不可能 present。属**需求/设计缺口**，补边需新增智能体→知识库契约载体（ADR/PM 决策），非数据修补 |
| 结论可追溯（E2E 第 10 项） | 蓝图 `bp_ent2_sc_ent2_group_month`：`status=BLUEPRINT_READY`、`approvalRequired=true`、`approvalRef=null`、`artifactDigest/contextDigest/traceId/actor/releasedTime` **均为 null**。未发布状态下空值符合预期，但意味着「关键结论→规则/框架版本/执行记录」的摘要链尚未产生；且 `GET /validate` 为 `passed=false`（历史自由文本 gaps），按门槛无法进入发布 |
| 沙盘布局（只读，E2E 第 9 项前置） | `GET /api/v1/workspace/scenarios/sc_ent2_group_month/sandbox/layout` → `200`，返回默认布局 7 节点（hub/数据集/本体实体/知识资产/AI Agent/安全策略/接口），未写库 |
| 预校验（只读检查） | `POST .../pre-validate` → `200`，`mind: pass=false（no active mind）`；`sandbox_coverage: pass=false（missing: DATASOURCE, KNOWLEDGE_ARTICLE, INTERFACE_REF, AGENT_PROFILE, ONTOLOGY_ENTITY, SECURITY_POLICY）`；`bindings_reachability: pass=true（覆盖 1/3，verdict=PARTIAL）` |

结论：E2E 的 10 项业务验证（正式口径拆分、确定性基准、证据/假设、情景敏感性、预测-实际-偏差关联、沙盘隔离、结论追溯）**无法在“K/C 两条阻断边缺失（其中 COGNITION 无契约载体）+ 正式口径读取 RLS 缺口 + 目标场景零运行”的前提下真实成立**；按任务书要求记录阻塞，不伪造通过。

## 4. 未通过/未执行汇总（不计为通过）

1. **年度经营预测 E2E（任务书第七阶段）**：未执行完。**2026-11-04 订正**：全栈服务已在线（gateway:8080 + 18081/18082/18083/18084/18086 + workspace:18090），此前"服务起不来"不再是阻塞点；当前阻塞为契约/数据缺口（见第 9~12 条与 §3.4）。前端未起、Playwright 未跑。未伪造通过。
9. **EXTRACTION 边契约**：5 条边均存在，但 `ContractSnapshot.isVerified()` 需 `syncedCount>0 && lastSyncTime!=null`。**2026-11-04 B28/B29 后**：本体发布与快照落盘已打通（`ont_entmgmt@1.0.1`），同步不再报「快照缺失」；剩余阻断 = 服务间调 DW 层 403（P1-12）+ 成功同步不落台账（P1-13），`lastSyncTime` 仍 null ⇒ 未 present。
13. **kb→DW 内部 REST 凭据缺口**：`POST /knowledge/sync/object/ont_entmgmt` 派生 job `FAILED / ECOS-DATA-041 …/engine/data/layers/CURATED (403)`；同端点 admin Token 200 ⇒ 需服务账号/Token 透传/mTLS 决策（P1-12），本任务未放宽鉴权。
10. **COGNITION 边契约**：无契约载体（`HttpContractProbeFacade` 直接抛异常），且 `ecos_agent_registry.metadata` 中知识库引用=0 ⇒ 需 ADR/PM 决策。
11. **正式口径读取**：`GET /api/v1/workspace/calibers` 403 `RLS_NO_POLICY_MATCHED`，31 条 RLS 策略无 `caliber` 命中 ⇒ 安全策略数据缺口，需授权补策略。
12. **场景就绪度**：`pre-validate` 实测 `mind=false（no active mind）`、`sandbox_coverage=false`（缺 DATASOURCE/KNOWLEDGE_ARTICLE/INTERFACE_REF/AGENT_PROFILE/ONTOLOGY_ENTITY/SECURITY_POLICY）；蓝图 `BLUEPRINT_READY`、`approvalRef=null`、摘要链未产生；目标场景 `ecos_scenario_run` 0 行、SANDBOX 0 行。
2. ~~**数据库迁移实跑**~~：**2026-11-04 已授权永久执行**。V311/V312 落库并复核（表/种子 4/3 列/3 索引/默认 FORMAL）；另发现并处理既有漂移：V244 执行 + `ecos_ontology`/`ecos_control` 两表幂等补建（0 行）。
3. **全量 common-api 门禁**：存在存量失败与用户未提交改动引起的编译失败，本任务未触碰。
4. **前端全量 vitest（jsdom）**：默认 Node 下 `ERR_REQUIRE_ESM`，未宣称全量绿；已用 node 环境跑受影响测试。
5. **Context Builder 权限裁剪器**：五类 sourceType 读取面均已接线（DATA_ASSET/KNOWLEDGE/ONTOLOGY/CAPABILITY/COGNITIVE，B10）；但 `ContextSecurityTrimmer` 无生产实现，必需源在真实宿主中恒 403（fail-closed，P1-9）。
6. **认知方法**：已实现内置字典 + 框架 `methods[]` 关联 + 只读目录端点；未提供方法独立写 API（按 §1.1 方法不是独立可部署资产）。
7. **执行记录持久化**：请求态内存记录，未落 runtime-task；进程重启丢失（设计允许，但未做恢复验证）；已携带 `contextDigest` + `contextSources` 版本追溯链。
8. **执行记录恢复**：未做中断后恢复验证（依赖 P1-3 决策）。
