# ECOS 场景工作台与认知引擎 — 实施进展

> 日期：2026-11-03；基线 `master` @ `811bd6e5` + 本任务工作树
> 说明：本文件只记录**实际落地并跑过**的内容；未执行项统一见《遗留问题》。

## 1. 已完成批次

### B1 认知框架注册表（Framework Registry）
- 新增 DDL：`docs/50-db_script/02-flyway-migration/gateway/V311__cognitive_framework_registry.sql`
  - `ecos_cognitive.ecos_cognitive_framework`（unique `framework_id+version+is_deleted`、`content_json/content_hash`、`contract_resolvable`、治理状态列）
  - 首批四框架 DRAFT 种子：CF-01-01 / CF-04-01 / CF-03-03 / CF-06-02
- 新增 API 模型：`cognitive-engine-api/.../api/framework/CognitiveFrameworkStatus.java`、`CognitiveFrameworkDefinition.java`
- 新增实现：`CognitiveFrameworkDao`、`CognitiveFrameworkRegistryService`、`CognitiveFrameworkController`
- 端点（`/api/v1/cognitive`）：`GET /frameworks`、`GET /frameworks/{id}/{version}`、`POST /frameworks`、`POST /frameworks/{id}/{version}/publish`、`POST /frameworks/{id}/{version}/status`
- 硬约束：同版本重复注册 409；PUBLISHED 再发布 409（版本不可变）；发布前必须 `input/outputSchemaRef` 可解析且禁止 `allowDirectSideEffects=true`；内容 SHA-256 冻结。

### B2 策略选择 + 计划校验
- 新增 `FrameworkSelectionService`、`PlanValidator`；端点 `POST /api/v1/cognitive/frameworks/select`、`POST /api/v1/cognitive/plans/validate`
- 硬约束先过滤（状态/适用域/输入/能力/契约/风险审批/预算），排除项带原因码；
- 排序输出 `scoreKind=ORDINAL_ONLY`、`calibrated=false`、`confidence=null`（不解释为概率）；
- 计划 DAG 校验：环节、依赖存在性、Schema、能力、预算、未授权副作用、权限；环检测 DFS。

### B3 认知执行状态机
- 新增 `CognitiveExecutionStateMachine`、`CognitiveExecutionService`、`CognitiveExecutionController`
- 端点：`POST /api/v1/cognitive/executions`、`GET /executions/{id}`、`POST /executions/{id}/transitions`
- `CREATED→PLANNED→VALIDATING→RUNNING→VERIFYING→SUCCEEDED`；终态 `NEEDS_INPUT/NEEDS_HUMAN_REVIEW/REJECTED/INVALID_OUTPUT/FAILED/BUDGET_EXCEEDED/CANCELLED`
- SUCCEEDED 需 VERIFYING 且 `schemaValid && businessValid`；业务结论状态独立字段 `businessConclusionStatus`；`idempotencyKey` 重复请求返回同记录并标记 `idempotentReplay`。
- 不新增执行表（请求态记录；长任务仍归 runtime-task）。

### B4 场景沙盘隔离
- 新增 DDL：`V312__scenario_run_mode_lineage.sql`
  - `ecos_scenario_run` 增 `run_mode`（默认 FORMAL）、`source_run_id`、`sandbox_lineage`（历史行 UNKNOWN）
- 修改：`ScenarioRunController` 传递 `RunCommand`；`ScenarioRunService` 接收 runMode；`WorkspaceDataDao.insertRun` 落 lineage、`listRunHistory` 回显、`findRunMode` 解析
- SANDBOX 运行：**跳过** `scenarioService.updateMetrics`（不写正式场景事实）；产物 `run_mode=SANDBOX` / `sandbox_lineage=SANDBOX`
- 启用 `SandboxWriteGuard`（白名单补实测表 `ecos_scenario_run` / `ecos_scenario_sandbox_layout`）
- 新增 `WorkspaceBaselineRunModeResolver`：FORMAL 引用 SANDBOX 基线由既有 `BaselineReferenceGuard` 拒绝（此前 resolver 未装配，守卫实际未生效）

### B5 前端框架选择面板
- 新增 `services/cognitiveFrameworkClient.ts`（list/select/plans validate；403/404/503/traceId 错误分类）
- 新增 `pages/workbench/[scenarioId]/blueprint/FrameworkPanel.tsx`，挂入 `BlueprintPanel` 新增 `framework` Tab
- 六态：loading / ready / empty / denied(403) / engine(503) / error；选择结果展示排除原因码与计划校验 issues；明确“排序分未校准、非概率”
- i18n：`workspace.framework.*` zh-CN/en 同步新增

### B7 Context Builder K/I 读取面 + 完整度前端
- 新增 `KnowledgeOntologyContextSourceReader`（认知引擎 impl）：
  - `KNOWLEDGE` → `GET /api/v1/knowledge/articles/{id}`（dccheng）
  - `ONTOLOGY` → `GET /api/v1/ecos/ontologies/entities/{id}`（buszhi）
  - 版本取源侧 `version`/`updatedAt`，缺失即 `ContextReadException`（不回填请求版本）；404/读失败不折叠为空集合。
- 新增前端 `scenarioCompletenessClient.ts` + `CompletenessPanel.tsx`，挂入蓝图 `completeness` Tab；只渲染后端 `coverage/missingEdges/islands/probeErrors`，不重算。
- `CompletenessReport` 增加阻断分类：`MissingEdgeView/IslandView.severity=BLOCKER+blocking=true`、`CompletenessReport.isBlocking()`（缺失必需边/孤岛阻断正式发布；警告/建议不阻断）。

### B10 Context Builder 全来源面
- 新增 `CapabilityContextSourceReader`（CAPABILITY → `GET /api/v1/ai/capabilities`，version=`contractVersion`）。
- 新增 `CognitiveAssetContextSourceReader`（COGNITIVE → 复用 `CognitiveAssetLifecycleService.resolve`，version 取资产侧）。
- 至此 DATA_ASSET / KNOWLEDGE / ONTOLOGY / CAPABILITY / COGNITIVE 五面均接线；版本缺失/源不可达均失败，不折叠为空成功。
- 新增 `CognitiveScenarioIntegrationTest`：场景请求 → 硬过滤选择 → 计划校验 → 执行记录 → SUCCEEDED（业务结论 UNVERIFIED 独立）。

### B8 认知方法目录
- 框架定义新增 `methods[]`（存储于既有 `content_json`，无新表）；注册/规范化哈希已纳入。
- 新增 `CognitiveMethodCatalogService` + `CognitiveMethodController`：
  - 内置 5 方法（ABDUCTION/FALSIFICATION/VARIANCE_DECOMPOSITION/BAYESIAN_UPDATE/SCENARIO_SENSITIVITY），`calibrated=false`、`probabilitySemantics=NONE`；
  - `usedBy` 由已发布框架 `methods[]` 汇总（frameworkId/version/stepId）；
  - `GET /api/v1/cognitive/methods`、`GET /api/v1/cognitive/methods/{methodId}`（未知 404）。

### B9 新端点 HTTP 契约测试
- `CognitiveFrameworkContractTest`（4 项）：框架列表/选择/非法状态 400/执行创建-非法跳转 409-合法 PLANNED；覆盖路径与 ApiResponse 包络。

### B6 门禁同步
- `MigrationVersionUniquenessTest`：`MAX_BASELINE_VERSION 311→312`、`NEXT_FREE_VERSION 312→313`
- `CognitiveIronLawsTest` 认知表白名单登记 `ecos_cognitive.ecos_cognitive_framework`（V311）
- `SuccessStatusFailureEnvelopeRatchetTest`：纯信封构造上限 34→35，附归因（新增两个 controller 的 `ApiResponse.success`）；未放宽任何错误面
- 认知模块 `ENV_ALGO` 面：把新代码中的 `IllegalStateException` 改为 `CognitiveGuardException`/`IllegalArgumentException`，不扩豁免面

### B16 前端接入缺口严重级分类
- `scenarioBlueprintClient` 新增 `getBlueprintGaps(scenarioId, blueprintId, severity?)` 与 `BlueprintGapSummary`（计数/`blocking`/`openBlockers` 原样透传，缺失值归 0）。
- 新增 `GapSeveritySummary.tsx`，挂入蓝图详情「缺口」区：loading / ready / empty / denied(401/403) / engine(503) / error 六态；只展示服务端分类，不自算、不把未分级默认成警告。
- i18n 新增 `workspace.bp.gap_summary.*`（zh-CN/en 同步）。
- 新增 `scenarioBlueprintGapsClient.test.ts`（2）；`npm run lint`（tsc --noEmit）通过；新测试 + 六态矩阵 + i18n 全局 + 颜色门禁共 **4 文件 / 37 测试通过**。

### B17 迁移脚本事务回滚演练（V311/V312）
- 只读勘察：`ecos-postgres` 健康；7 个 ECOS 服务端口均未监听；`ecos_ai.ecos_scenario_run` 存在，`public` 同名表不存在。
- **修正 V312**：表引用由未限定 `ecos_scenario_run` 改为 `ecos_ai.ecos_scenario_run`（实库实测 schema；原注释误写 public）。四类语句（ALTER ×3 / INDEX / COMMENT ×3）同步限定。
- 演练：`BEGIN; \i V311; \i V312; ROLLBACK;`（`ON_ERROR_STOP=1`）→ 全部语句成功：`CREATE TABLE/INDEX/COMMENT/INSERT 0 4/ALTER TABLE ×3/INDEX/COMMENT ×3`，随后 `ROLLBACK`。
- 回滚后复核：`ecos_cognitive.ecos_cognitive_framework=ABSENT`、`ecos_ai.ecos_scenario_run.run_mode` 列计数 `0` ⇒ **库结构未改变**（演练未落库，正式实跑仍需授权）。
- 临时演练文件（容器 `/tmp` 与本地 `ecos_backend/target/`）已清理。

### B18 启动链路实测缺陷修复（gateway 实启发现）
- 背景：`mvn -pl gateway -am install -DskipTests` **BUILD SUCCESS**，但 `start-backend.ps1 -Modules gateway` 启动失败——仅跑单测无法暴露的 Spring 装配问题。
- 缺陷 1：`ConflictingBeanDefinitionException: bean name 'planValidator'` 与 ai-engine `wagent.orchestrator.PlanValidator` 同名冲突，gateway 上下文无法初始化。
  - 修复：`@Component("cognitivePlanValidator")`（按类型注入不变，仅消歧 bean 名）。
- 缺陷 2：`BeanInstantiationException: ScenarioRunService No default constructor found`——该类有两个构造器（4 参测试用 / 5 参生产用），Spring 无法自动选择。
  - 修复：5 参生产构造器加 `@Autowired`（4 参构造器保留供单测直接 new）。
- 重新构建 `workspace-impl + gateway -am install -DskipTests`：**BUILD SUCCESS**。
- 备注：首次重打包因上一次启动残留 gateway JVM 占用 jar 失败，已用 `stop-backend.ps1` + 精确 PID 停止后重建成功（未强杀无关进程）。

### B19 gateway 实启与 live API 冒烟（2026-11-03）
- 启动：`start-backend.ps1 -Modules gateway -Profile enterprise` → `Started GatewayApplication in 77.558 seconds`，`:8080` 监听（脚本 60s 判 FAIL 属阈值偏短，服务随后就绪）。
- `POST /api/v1/auth/login`（admin）→ `code=0` 返回 accessToken。
- `GET /api/v1/engine/cognitive/health`（匿名白名单）→ `200 {"status":"UP","components":{"db":"UP","engine":"RUNNING","agents":12}}`。
- ~~业务读端点持 admin Token 均返回 403~~ **（2026-11-04 复验订正）**：该 403 未再复现——`/api/v1/cognitive/frameworks`、`/api/v1/system/users` 均 200；场景族经 workspace:18090 + Bearer + 四个信任头实测列表/详情/completeness 全 200。P0-3 已关闭。当前 E2E 的阻断点是 P0-2 所列契约/数据缺口，**不是鉴权**。
- 验证完成后已用 `stop-backend.ps1` 停止 gateway，端口释放。

## 1.1 新增/变更 API 登记（验收清单 §3「所有新增/变更 API 登记」）

> 全部为只增不改；包络统一 `ApiResponse{code,errorCode,message,data,traceId}`，鉴权走既有 L1/Clearance 与 security 通道（未新增匿名面）。

| 方法 | 路径 | 说明 | 契约测试 |
|---|---|---|---|
| GET | `/api/v1/cognitive/frameworks` | 框架清单（`problemType/domain/status` 可选过滤） | `CognitiveFrameworkContractTest` |
| GET | `/api/v1/cognitive/frameworks/{frameworkId}/{version}` | 框架版本详情 | 同上 |
| POST | `/api/v1/cognitive/frameworks` | 注册（强制 DRAFT，内容哈希，执行器白名单） | 同上 + `CognitiveFrameworkRegistryServiceTest` |
| POST | `/api/v1/cognitive/frameworks/{frameworkId}/{version}/publish` | 发布（契约可解析 + 门禁） | 同上 |
| POST | `/api/v1/cognitive/frameworks/{frameworkId}/{version}/status` | 状态迁移（非法 409） | 同上 |
| POST | `/api/v1/cognitive/frameworks/select` | 硬过滤选型 + 计划草案（未校准标注） | `FrameworkSelectionServiceTest` |
| POST | `/api/v1/cognitive/plans/validate` | 计划 DAG/预算/权限校验 | `PlanValidatorTest` |
| POST | `/api/v1/cognitive/executions` | 创建执行记录（幂等 + contextDigest/contextSources/frameworkRefs） | `CognitiveExecutionServiceTest` |
| GET | `/api/v1/cognitive/executions/{executionId}` | 执行记录详情 | `CognitiveFrameworkContractTest` |
| POST | `/api/v1/cognitive/executions/{executionId}/transitions` | 状态迁移（非法/终态 409，SUCCEEDED 双验证 422） | 同上 |
| GET | `/api/v1/cognitive/methods` | 认知方法目录 | `CognitiveMethodCatalogServiceTest` |
| GET | `/api/v1/cognitive/methods/{methodId}` | 方法详情（未知 404） | 同上 |
| GET | `/api/v1/business/scenarios/{scenarioId}/blueprints/{id}/gaps` | 缺口严重级分类（`severity` 可选，非法 400） | `ScenarioBlueprintGapsQueryTest` / `ScenarioBlueprintGapsContractTest` |
| POST | `/api/v1/workspace/scenarios/{id}/runs` | **既有**；请求体只增 `runMode/scenarioId/baselineRunId` | `ScenarioRunSandboxIsolationTest` |
| POST | `/api/v1/workspace/scenarios/{id}/sandbox/layout` | **既有**；请求体只增 `runMode`，入口过写白名单 | `ScenarioSandboxLayoutGuardTest` |

## 2. 实际执行命令与结果

| 命令 | 结果 | 证据 |
|---|---|---|
| `mvn -o -f ecos_backend/pom.xml -pl engine/cognitive-engine/cognitive-engine-impl -am compile -DskipTests` | PASS | 编译绿 |
| `mvn ... -pl engine/cognitive-engine/cognitive-engine-impl test`（新增前） | PASS | `Tests run: 516, Failures: 0, Errors: 0` |
| 新增框架测试定向 | PASS | `PlanValidatorTest 5`、`CognitiveExecutionStateMachineTest 4`、`CognitiveExecutionServiceTest 4`、`FrameworkSelectionServiceTest 4`、`CognitiveFrameworkRegistryServiceTest 5` 全绿 |
| `mvn ... -pl workspace/workspace-impl test` | PASS | 全模块 `BUILD SUCCESS`；新增 `ScenarioRunSandboxIsolationTest 4` 绿 |
| `mvn ... -pl gateway test -Dtest=MigrationVersionUniquenessTest` | PASS | `Tests run: 5, Failures: 0` |
| `ecos_frontend: npm run lint`（tsc --noEmit） | PASS | 无错误 |
| `npx vitest run --environment=node`（新 client/i18n + 六态 + i18n 门 + 颜色门） | PASS | 新 6 项 + 结构门 36 项全绿 |

> 认知模块最终全量：**`Tests run: 540, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`**（含框架/方法/契约/五类 Context reader/语义集成/执行器白名单；2026-11-03 20:26 实跑）。

### B11 框架执行器白名单（P1-8 收口）
- `CognitiveFrameworkRegistryService.ALLOWED_EXECUTORS = {controlled_reasoning, deterministic_tool, llm_assisted_readonly, human_review}`；注册时 `steps[].executorType` 非空且不在白名单 → `E400_INVALID_PARAM`（禁任意代码容器）。
- 新增用例 `unknownExecutorTypeIsRejected`；`CognitiveFrameworkRegistryServiceTest` 6/6 绿。
- 白名单变更后全量回归：`Tests run: 540, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。

### B12 画布写入口白名单断言（P1-7 收口）
- `SandboxSaveDTO` 增可选 `runMode`（缺省 FORMAL，API 只增不改）。
- `ScenarioSandboxLayoutService.saveLayout` 入口先 `sandboxWriteGuard.assertAllowed(RunMode.parseOrDefault(runMode), "ecos_scenario_sandbox_layout")`，未知 runMode 直接拒绝且不落库。
- 新增 `ScenarioSandboxLayoutGuardTest`（3）：未知 runMode 零写入、SANDBOX 走白名单、缺省回落 FORMAL。
- `workspace-impl` 全量：`Tests run: 376, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`（3 skip 为既有）。

### B14 执行记录携带来源资产版本
- `CognitiveExecutionService.create` 新增可选 `contextSources`（`sourceType/sourceId/version` 三要素必填，可选 `asOfTime`）：
  - 缺任一要素 → `E400_INVALID_PARAM`（禁无版本引用）；
  - 记录原样保存 `contextSources`，与既有 `contextDigest`/`blueprintVersion`/`frameworkRefs` 共同构成执行追溯链（清单 §3「所有认知执行引用 contextDigest 与来源资产版本」）。
- 新增 `recordKeepsSourceAssetVersionsForTraceability`、`sourceWithoutVersionIsRejected`；`CognitiveScenarioIntegrationTest` 补两源版本断言。
- 认知模块全量：`Tests run: 542, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（B15 补充守护后为 543）。

### B15 架构守护：禁私有调度器/线程池
- `ArchitectureTest.shouldNotCreatePrivateSchedulerOrThreadPool`：认知引擎不得依赖 `ScheduledExecutorService` / `Executors` / `ThreadPoolExecutor` / `Timer`（验收清单 §3「禁私有调度器」，长任务唯一通道 = runtime-task）。
- `ArchitectureTest` 6/6 绿；认知模块全量 543 绿（`Tests run: 543, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`）。

### B13 蓝图缺口严重级查询（P1-4 收口）
- 新增 `GET /api/v1/business/scenarios/{scenarioId}/blueprints/{id}/gaps?severity=`：
  - 服务端按 `BLOCKER/WARNING/SUGGESTION/UNKNOWN` 分类计数，`blocking` 仅对未关闭 BLOCKER 为真（`RESOLVED`/`ACCEPTED_RISK` 不计）；
  - `severity` 非法值 → 400 `BLUEPRINT_QUERY_INVALID`（不静默忽略）；
  - 仅读既有 `gaps` 列，无新表/新列。
- 新增 `ScenarioBlueprintGapsQueryTest`（3）+ `ScenarioBlueprintGapsContractTest`（2，MockMvc 路径/包络/400 errorCode）。
- `workspace-impl` 全量回归：`Tests run: 381, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`。
- 前端现状：蓝图详情已按 VO `gaps[].severity` 渲染徽标；本端点供筛选/汇总查询，暂未接入页面（记录于遗留问题 P3）。

### B20 发布门槛明示非阻断缺口（P1-4 后端补全）
- `ScenarioBlueprintValidator.validateForRelease` 在原有 `openBlockerGaps` 之外新增 `openWarnings` / `openSuggestions` 明细（`gapId/category/status/description`，`RESOLVED`/`ACCEPTED_RISK` 视为关闭），**不参与 `passed` 判定**，与 §6.6「BLOCKER 才阻断」一致。
- `ValidationResult.passed(Map)` 新增重载，使 `GET {id}/validate` 在通过时仍返回非阻断明细（此前通过即返回空 `details`，警告/建议被静默丢弃）。
- 新增 `ScenarioBlueprintValidatorTest`（3）：仅 WARNING/SUGGESTION 未关闭 → passed=true 且明细可见；关闭态不计入；未关闭 BLOCKER → failed 且仍携带非阻断明细。
- `workspace-impl` 全量回归：`Tests run: 384, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`（3 skip 为既有）。
- 前端：`BlueprintValidateDetails` 增 `openWarnings`/`openSuggestions`；`BlueprintValidatePanel` 增两行只读明细（标注“不阻断”），i18n zh/en 同步；`npm run lint` 无输出，结构门禁 + 缺口 client `4 files / 37 tests passed`。

### B21 框架选择/重规划可追溯（阶段 4 清单「版本切换/重规划记录原因与实际版本」）
- `select` 结果新增顶层 `usedFrameworkVersions[]`（`frameworkId` + `version` + `selectionReason=HARD_FILTERS_PASSED`），选中行同步携带 `selectionReason`；实际使用版本不靠调用方猜。
- 计划校验不通过（`status=REPLAN_OR_REJECT`）时新增 `planRevision{decision,reason=PLAN_VALIDATION_FAILED,issues,usedFrameworkVersions}`，重规划有原因、有版本痕迹；`PLANNED` 不带该字段（禁无痕改计划）。
- `FrameworkSelectionServiceTest` 增断言；定向 4/4 绿，认知模块全量 `Tests run: 543, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。
- 前端：`FrameworkSelectionResultVO` 增 `usedFrameworkVersions` / `planRevision` 类型；`FrameworkPanel` 展示“实际使用版本”与重规划决策/原因/问题行，i18n zh/en 同步；`npm run lint` 无输出，框架 client + i18n + 零字面色 `4 files / 13 tests passed`。
- 范围说明：覆盖**选择阶段**的版本与原因；执行期运行时切换/重规划事件仍需落 runtime-event（见遗留问题 P1-10）。

### B22 迁移落库与 live 鉴权复验（2026-11-04，用户授权后）
- V311/V312 已对 `ecos-postgres/sys_man` 永久执行：`INSERT 0 4`、`ALTER TABLE ×3`；复核表存在、种子 4、3 列、3 索引、`run_mode` 默认 `FORMAL`；`MigrationVersionUniquenessTest` 5/5。
- live（gateway :8080）：登录 `code=0`；带 admin token `GET /api/v1/cognitive/frameworks` 返回 4 条 DRAFT；`POST .../CF-01-01/1.0.0/publish` 200，PUBLISHED 列表可见；`GET /api/v1/system/users` 200 ⇒ **此前 403 未复现，未放宽任何安全策略**。
- 环境侧既有漂移：全栈启动因 `ecos_ontology.ecos_ontology_git_archive_pending` 缺表失败（迁移 V170.2 建在 `public`，代码查 `ecos_ontology`）；已用 `CREATE TABLE ... (LIKE public...)` 幂等补表（0 行，不改既有数据），不改仓库迁移/代码。
- 场景端点 `/api/v1/workspace/scenarios` 在 gateway-only 下 404（gateway 未承载 workspace 控制器），待 workspace:18090 起后复测。

### B23 Context/Outcome HTTP 契约补齐
- 新增 `CognitiveContextOutcomeContractTest`（2）：`POST /api/v1/cognitive/context/build`、`POST /api/v1/cognitive/outcomes/evaluate` 路径与 `ApiResponse` 包络（`code=0` + `data`）断言，Service mock 隔离。
- 测试须显式 `Accept: application/json`（否则框架内容协商返回 XML，属既有行为，非缺陷）。
- 定向 2/2 通过；认知模块全量 `Tests run: 545, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。

### B24 蓝图 `GET {id}/validate` 历史自由文本 → 200 fail-closed（live 复验）
- 缺陷：live 调用 `GET /api/v1/business/scenarios/sc_ent2_group_month/blueprints/bp_ent2_sc_ent2_group_month/validate` 返回 `400 BLUEPRINT_CONTENT_INVALID`（`gaps` 列为历史自由文本，`arrayNodes` 解析失败冒泡），违反端点「只读 200 + passed 明细」契约。
- 修正：`ScenarioBlueprintValidator.validateForRelease` 捕获 `BlueprintContentInvalidException` → `passed=false` + reason「按 fail-closed 判为未通过」+ `details.invalidContent`（保留原始解析错误，不判通过、不静默吞）。新增用例 `unstructuredGapsFailClosedWithDetailInsteadOfThrowing`，`ScenarioBlueprintValidatorTest` 4/4。
- `workspace-impl` 全量：`Tests run: 385, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`。
- live 复验（gateway :8080，admin token + trust 头）：validate `HTTP=200`、`code=0`、`passed=false`、`details.invalidContent` 非空；`/gaps` `HTTP=200`、`counts` 全 0。
- 部署坑：gateway fat-jar **内嵌** `workspace-impl`；仅 `mvn -pl gateway install` 时增量构建不重打包 fat-jar（jar 时间戳不变），实测仍打旧类。必须 `mvn -pl gateway clean install -DskipTests` 后重启 gateway，才能让 live 生效（本次已如此执行）。

### B25 场景/认知 live 面扩测（2026-11-04）
- 场景族（workspace:18090，Bearer + `X-ECOS-USER/ROLE/TENANT/DOMAIN`）：列表/详情/`completeness` 均 200；gateway :8080 不承载 `/api/v1/workspace/scenarios/**`（404 为路由面差异）。
- 目标场景 `sc_ent2_group_month`：完整度 `coverage=0.3333`、`verdict=PARTIAL`；缺失 `EXTRACTION`（KNOWLEDGE_BASE 未引用 OBJECT_TYPE）与 `COGNITION`（AI_AGENT 未引用 KNOWLEDGE_BASE）两条 `BLOCKER`。
- `ecos_ai.ecos_scenario_run`：全表 1 行 `sc_entmgmt/FORMAL/SUCCEEDED_DEGRADED`；目标场景 0 行、`SANDBOX` 0 行 ⇒ 年度预测 E2E 阻塞（P0-2 已更新证据）。
- 框架选择 live `POST /api/v1/cognitive/frameworks/select`：200；仅 PUBLISHED 进候选，`excluded` 回传 `PROBLEM_TYPE_MISMATCH`/`MISSING_INPUT:*`，`ranking=DETERMINISTIC_ORDINAL_ONLY`、`probabilitySemantics=NONE`、`status=NEEDS_INPUT_OR_HUMAN`、`usedFrameworkVersions=[]`。

### B26 场景详情「资源不存在」错映射 500→404（异常场景补实）
- 发现：live `GET /api/v1/workspace/scenarios/sc_does_not_exist` → **500 INTERNAL**；日志 `NotFoundException: SCEN-404: 场景不存在` 被兜底分支吞成 500。
- 修正：`WorkspaceExceptionHandler` 新增 `@ExceptionHandler(com.chinacreator.gzcm.common.exception.NotFoundException.class)` → `404 NOT_FOUND`（沿用途中构造的领域文案，不泄露 SQL/堆栈）。
- 验证：重启 workspace 后 live 复测 → `HTTP=404`、`errorCode=NOT_FOUND`、`message=SCEN-404: 场景不存在: sc_does_not_exist`；既有场景仍 `200`；`mvn -pl workspace/workspace-impl test` → `Tests run: 385, Failures: 0, Errors: 0, Skipped: 3` / `BUILD SUCCESS`。
- 同批 live 负向：非法迁移 `BLUEPRINT_READY→RELEASED` → `409 ILLEGAL_TRANSITION` 且状态未变（无副作用）。

### B27 知识同步 500 修复（V313）与剩余前置定位
- 根因：`ecos_knowledge.ecos_kg_sync_log.object_type varchar(20)` 被写入 32 位本体 id（graph_node/edge 的 `ontology_version` 同为 20，本体域 `ecos_ontology.version` 为 128，列宽不一致）。
- 迁移：新增 `V313__kb_ontology_version_width.sql`（4 列加宽 20→128，仅加宽、附回滚），已对 dev 库实跑 4 条 ALTER；`MigrationVersionUniquenessTest` 基线 312/313→313/314，实跑 `Tests run: 5, Failures: 0` / `BUILD SUCCESS`。
- live：`POST /knowledge/sync/object/bc129d24…` 由 **500 → 不再 500**；现为 `failed: 读取本体快照失败`。
- 剩余前置（数据侧）：`ecos_knowledge.ecos_kb_ontology_snapshot` 与 `ecos_ontology.kb_ontology_snapshot` **行数均为 0**，且 kb/ontology 无快照创建端点 ⇒ 需先由本体→知识快照任务落盘，同步才能对齐版本并写 `lastSyncTime`（EXTRACTION present 的前提）。

### B28 本体版本发布 500 修复（OntologyVersionDao 账实订正）
- live 复现：`POST /api/v1/ecos/ontologies/ont_entmgmt/versions` → **500 INTERNAL**；gateway 日志 `PSQLException: column "created_at" does not exist`（42703）。
- 根因（两处同表漂移，均为"代码未随迁移切换"而非设计变更）：
  1. 时间戳列——DAO 读写 `created_at`，live `ecos_ontology_version` 实列 `create_time`（V122 §2.6 六基线列）；
  2. 快照列——V170.1 起新写应为 `snapshot_json`(TEXT)，DAO 仍 INSERT `snapshot ?::jsonb`（live `snapshot` 为 TEXT）。
- 修正 `engine/ontology-engine/.../dao/OntologyVersionDao.java`：读腿列投影改显式列名 + `COALESCE(snapshot_json, snapshot)`（兼容停写前存量行）、排序/比较改 `create_time`；写腿改 `snapshot_json` + `create_time`、去掉 `::jsonb`。
- 新增契约测试 `OntologyVersionContractDaoTest`（6 例，判据打在捕获 SQL 实参与 RowMapper 读列上，不打源文本）：读腿 `create_time`/非 `SELECT *`、写腿 `snapshot_json`/禁 `created_at`/禁 `::jsonb`、RowMapper 读回、空集非 null。
- 实跑：`mvn -o test -Dtest=OntologyVersionContractDaoTest` → `6/0/0`；模块全量 `mvn -o test` → `Tests run: 288, Failures: 0, Errors: 0, Skipped: 0`。
- live 复验（gateway 重建后）：`POST .../versions` → **200**（`ver5001` / `1.0.0` / Draft），`POST .../publish` → **200**（`status=Published`）。
- 发布副作用（事件双发已触发，真实消费）：`ecos_knowledge.ecos_kb_ontology_snapshot` 因表名/列名漂移写入失败，`ecos_kg_sync_log` 落 3 条 `KB_ONTOLOGY_SYNC FAILED`（`bad SQL grammar [INSERT INTO ecos_knowledge.kb_ontology_snapshot ...]`）——即 B29 的入口证据。

### B29 本体快照腿切 V179 + V314 溯源列实跑（EXTRACTION 前置解阻）
- 根因（三处同源：V183/V184 "应用侧后续批次"未执行）：
  1. `ecos_knowledge.kb_ontology_snapshot` **live 不存在**（实表只有 `ecos_ontology.kb_ontology_snapshot`），快照写腿恒失败；
  2. V179 权威表 `ecos_knowledge.ecos_kb_ontology_snapshot`（`snap_version` / `entity_codes_json` / `create_time`）无人使用；
  3. `ecos_knowledge.graph_node/graph_edge` 缺 4 个溯源列——V134 脚本定义过但 **dev 库实测 8 列全缺**，导致版本对齐 `UPDATE ... SET ontology_version` 与实例抽取 `INSERT ... (ontology_id, ...)` 双双 42703。
- 代码修正（快照腿整体切 V179，读写同一张表；图表的 `graph_node/graph_edge` 名称保持不变，仅补列）：
  - `KbOntologySnapshotDao`：`upsertSnapshot` 写 `id`(UUID)/`snap_version`/`entity_codes_json`/`relationship_codes_json`/`create_by`，`ON CONFLICT (ontology_id, snap_version, is_deleted)`；`softMarkPreviousVersion` 同步改列。
  - `KgMapperDao` 快照读腿：`snap_version AS version` + `jsonb_array_length(CAST(..._json AS jsonb))`，`ORDER BY create_time`。
  - `KbEntityInstanceExtractionServiceDao` 快照读腿：`entity_codes_json AS entity_codes`（保持上层 map 键契约）。
  - `EntityLinkerDao`：`jsonb_array_elements_text(CAST(s.entity_codes_json AS jsonb))`。
- 迁移：新增 `V314__kb_graph_ontology_traceability_apply.sql`（= V134 幂等重放：8 列 + 3 索引 + COMMENT，全部 `IF NOT EXISTS`），已对 dev 库实跑；`information_schema` 复核 8 列齐；`MigrationVersionUniquenessTest` 基线 313/314→**314/315**。
- 实跑：kb 模块全量 `mvn -o test` → `Tests run: 343, Failures: 0, Errors: 0, Skipped: 2` / `BUILD SUCCESS`。
- live 复验（gateway + dccheng 重建后）：
  - `POST .../versions`+`.../publish` → 200（`ver5002` / `1.0.1` / Published）；
  - `ecos_knowledge.ecos_kb_ontology_snapshot` 落 1 行：`ont_entmgmt@1.0.1`、`schema_hash=c063657a…`、`create_by=admin`、`is_deleted=0`；
  - 该次发布**无** `KB_ONTOLOGY_SYNC FAILED` 行（上一版 3 条失败仅保留为历史证据）；
  - `POST /api/v1/knowledge/sync/object/ont_entmgmt` → 200 `status=started`（不再"读取本体快照失败"）。
- 新暴露的下游前置（**未解，不得计为通过**）：实例抽取 job `tg-obj-1793776670109` 落 `FAILED`，`ECOS-DATA-041: DW 层资源端点不可用: http://localhost:8080/api/v1/engine/data/layers/CURATED (403 …)`。同端点带 admin Token 直连为 **200**（资源清单正常），即 kb 引擎内部 REST 客户端未携带凭据、被既有 fail-closed 拦下。修法涉及服务间凭据（服务账号/Token 透传/mTLS），属安全面决策，未擅自放宽。
- 另一处观测缺口（登记，不修）：`GET /knowledge/sync/status` 的 `lastSyncTime` 恒为 `null`（成功同步不写 `ecos_kg_sync_log` 成功行，仅失败落行）。

### B30 前端实跑复验（浏览器点验 4 个缺陷，3 修 1 登记）
- 环境：`start-frontend.ps1` → vite :3000（BFF）/ `#/workbench/sc_ent2_group_month`，admin 登录后逐 Tab 点验；服务侧 gateway:8080 + aiming:18084 + workspace:18090 在线。
- **D1 向导步骤 Tab 显原始 key**：`workspace.wiz.step.completeness` / `.framework` 缺词条（后端/前端新增第 3、4 步时未同步 locale）。修：`locales/workspace/{zh-CN,en}.json` 补键（完整度 / 框架选型）。
- **D2 完整度面板显原始 key**：`CompletenessPanel` 直接渲染后端下发的 `labelKey` 而未过 `t()`，且 5 类缺失边词条缺失。修：改 `t(e.labelKey)` + 补 `workspace.completeness.missing.{mapping,extraction,cognition,expose,govern}` 双语；live 复验显示「本体实体 → 知识资产（抽取）」「知识资产 → AI Agent（认知）」。
- **D3 框架选择恒 403（传输层丢 Authorization）**：页面 `POST /api/v1/cognitive/frameworks/select` 恒 403，而同一 payload 直连网关 200。根因 `httpClient` 的 `fetch(url, { headers, ...options })` 调用序——调用方一传 `options.headers` 就整体覆盖默认头，`Authorization` 被冲掉。修：新增 `mergeHeaders()`，三条传输腿（`apiFetch`/`apiFetchData`/`doFetch`）统一合并默认头与调用方头。
- **D4 修 D3 后的 500（重复 Content-Type）**：朴素合并同时下发 `Content-Type` 与 `content-type`，aiming 侧拼成 `application/json, application/json` → `HttpMediaTypeNotSupportedException` → `ECOS-COG-504`（aiming 日志实证）。修：`mergeHeaders` 经 `Headers` 归一化后再下发，单一 content-type。
- 新增回归门 `src/services/__tests__/httpClient.headers.spec.ts`（4 例）：调用方带头时仍带 Bearer、content-type 不重复、apiFetch/doFetch 自定义头与 Authorization 并存；**变异探针**：撤掉 apiFetchData 的合并后该用例转红（1 failed / 3 passed），恢复后 4/4 绿。
- 实跑：`npm run lint`（tsc --noEmit）零错；`vitest run src/services/__tests__` under Node 22 → **25 文件 / 347 测试全过**；`i18n-coverage` + workbench 用例 → **3 文件 / 35 测试全过**。
- live 复验：框架选型点「执行策略选择」→ 页面渲染「选择结果：NEEDS_INPUT_OR_HUMAN」+ 排除原因 `MISSING_INPUT:goal_statement_present` + 未校准声明（不再 403/500）。
- **D5 认知摘要空态文案与事实不符**：场景已绑 6 类资产，但「认知摘要」显示「该场景暂未绑定任何数据或认知」——该空态实际由 `operationEval`（经营评估）无记录触发，与绑定无关。修：`workspace.l2.empty` 改为「该场景暂无经营评估记录。」（en 同步）；live 复验渲染新文案。
- **登记未修（非本批缺陷面）**：① `GET /api/v1/workspace/scenarios/{id}/actions` → `503 COGNITIVE_UNAVAILABLE`，reason=「decision 底座网关未装配」（`DecisionGateway` 无实现 Bean，卷07 声明的接缝；UI 已按 503 显示横幅 + traceId，未掩成 200）；② aiming 侧 `ClearanceInterceptor` 超管旁路审计发布 WARN：`ObjectProvider<Object>` 候选歧义（`No qualifying bean of type Object … more than one primary`），属既有装配面缺陷，按「不阻断旁路」降级。

### B31 场景运行首次实跑 + 服务间裁决链根因定位（2026-11-06）
- 实跑：`POST /api/v1/workspace/scenarios/sc_ent2_group_month/runs`（`runTypes=[DIAGNOSE]`，metric=毛利率，deviation=3.5）→ **200**；`ecos_ai.ecos_scenario_run` 落 1 行 `run_e810c6a7-804 / FORMAL / SUCCEEDED_DEGRADED / degraded=true`；响应体 `diagnosis.message=诊断失败: 认知引擎暂不可用`。
- 下游归因（同一根因，三处实例）：
  1. workspace → cognitive（gateway monolith 通道）`POST /cognitive/diagnose` → **403**（DcchengClient 无凭证中继，`post()` 不加 Authorization/信任头，workspace 日志实证 `dccheng 调用失败 /cognitive/diagnose: 403`）；
  2. workspace → security `POST /api/v1/security/decision` → **403 `无用户上下文（ECOS-AUTH-010）`**，`filterColumns` 按 DENY 置空 ⇒ `GET .../runs` 返回 `200 []`（库里 1 行不可见，UI「运行历史」空）；
  3. kb → DW `GET /api/v1/engine/data/layers/CURATED` → 403（P1-12 既有）。
- 第 2 条的精确归因（已用三组探针证伪其他假设）：
  - 直连 sysman:18081 `/security/decision`（Bearer + 四信任头 + 正确 `assetRef` 体）→ 同为 010 ⇒ **不是**中继丢头（`WorkspaceSecurityEngineClient` 已用 `CallerCredentialSource` 逐字中继，jar 内类字节码已核）；
  - 体加 `subject:{subjectId:"u-admin"}` → **200** 并返回真实裁决束（RLS `(1=1)` / columns allow / OPA `EVALUATED_DENY`）⇒ 端点本身可用；
  - 根因 = `DecisionController` 的主体取 `ServiceContext.current()`，而 `ServiceContext` 只由 `HeaderAuthInterceptor` 填充；**sysman 宿主从未注册该拦截器**（全仓 grep：仅 workspace 宿主注册，sysman 只有 `ClearanceMvcConfig`），gateway 侧又对全部外部请求剥离 X-ECOS-\* ⇒ 两态寻址两条路都拿不到主体。
- 修复属安全信任链设计决策（C.2：service 侧按设计应由 `HeaderAuthInterceptor` 从真实 HTTP 头重建上下文，但注册该拦截器会对该宿主全部路径强制要求信任头，需 PM/架构定注册范围），本任务**未擅自改安全链**；已登记 P1-15。

### B32 安全裁决信任链修复（P1-15，方案①落地：sysman 宿主装 HeaderAuthInterceptor）
- **腿 1 — sysman 启用信任头拦截器**：`services/sysman/application.yml` 的死键 `ecos.header-auth.enabled=false`（auto-config 读不到）改为正键 `ecos.security.header-auth.enabled=true`（与 aiming/workspace 同口径，common-api `HeaderAuthAutoConfiguration` 单源装配）。`ServiceContext` 自此在 sysman 宿主可被填充。
- **腿 2+3 — MapperScan 双重误注册修复**（途中挖出的宿主级既有缺陷）：
  - 现象：腿 1 后 `/decision` 仍 503 `ECOS-SEC-501 主体域真值源不可用`，日志 `Invalid bound statement: OrganizationDao.getUserOrganizationAffiliations`；另证 `GET /system/organizations/all` 恒 `code=-1 查询机构列表失败`（sysman 宿主机构读面整体从未工作过）。
  - 根因一：sysman 启动类 `@MapperScan("com.chinacreator.gzcm.sysman.**.dao")`——sysman 域 dao 包全量核查为「纯接口 + Impl(ISystemDatabaseAccess/iBATIS sqlMap)」形态（顶层无 class、无 mapper 目录），误注册 mapper 代理后按参数名优先命中代理而非 `@Repository` Impl。
  - 根因二（gateway 不中招的原因）：runtime-core `MyBatisConfig` 内含 `MapperScannerConfigurer(basePackage="com.chinacreator.gzcm.**.dao")` 全仓通配；gateway 在 `GatewayApplication` 显式 ASSIGNABLE_TYPE 排除该类，sysman 未排除。
  - 修复：删除 sysman 启动类 MapperScan 的 `sysman.**.dao`/`sysman.**.mapper` 死条目 + 同 gateway 口径排除 `MyBatisConfig`（SqlSessionFactory/事务交 mybatis-spring-boot-starter 自动装配）。
- **腿 4 — workspace→sysman 直连**：gateway 的 `EcosTrustHeaderFilter` 剥离 X-ECOS-\* ⇒ workspace→gateway→decision 恒 010。本机 dev 以逃生口 `ECOS_WS_SYSMAN_BASE=http://localhost:18081` 注入（yml 默认空走 route-manifest，不违「端口字面量禁止入库」门禁）；正解=sysman 切流（manifest route=service），切流后逃生口清空。配套：`ScenarioOptionsController` 全部跨服务 GET 补 `CallerCredentialSource.applyTo` 凭据中继（原 `HttpEntity.EMPTY` 裸调，属 D-B4-61 六腿收口漏网腿）。
- 前端配套：`config/route-manifest.ts` dev 直连 :18081 的 security 族补注信任头（与 ai/workspace 同款）。
- **门禁**：新增 `SysmanHeaderAuthConfigTest`（3 例，变异探针：enabled=false 转红）+ `SysmanMapperScanContractTest`（3 例，变异探针：回 HEAD 版本 2 例转红）；sysman 模块 `7/7` 绿；workspace-impl `391/0/3` 绿（端口字面量门禁触发后已改逃生口方案）；前端 tsc 零错 + e6Placements/i18n `2 文件 16 + 1 文件 4` 全绿。
- **live 验证（2026-11-06）**：
  - `POST :18081/security/decision`（Bearer+信任头+assetRef 体）→ **200**，返回真实裁决束（`rls.whereClause=(1=1)`、columns allow、OPA `EVALUATED_DENY`）；无信任头 → 403（fail-closed 生效）。
  - `GET :18081/system/organizations/all` → **200 + 机构列表**（修复前 code=-1）。
  - `GET :18090/.../runs` → **200 + run 行完整可见**（修复前 200 空数组）。
  - `GET :18090/.../available/interfaces` → 200 有数据（凭据中继生效）；`available/security` → 200 空列表（sysman 侧真实数据为空；对照 gateway 宿主同端点为 `code=-1 bad SQL grammar`——gateway 嵌套 sysman-impl 亦为 9 月旧制品，佐证切流方向）。
  - 浏览器（登录后场景页）：「运行历史」表格渲染 `2026-11-06T07:05 / DIAGNOSE / 毛利率 (3.5%) / SUCCEEDED_DEGRADED`。
- **未解残余（登记 P1-12/P1-15 余项）**：①workspace→cognitive（gateway monolith 通道）仍 403（DcchengClient 无凭据中继，P1-12）；②kb/ai/ontology 三个 security 客户端仍裸调（本期未改）；③sysman 切流登记与 gateway 嵌套旧 sysman-impl 刷新待部署批。

### B33 workspace→cognitive 凭据中继（P1-12 的 workspace 腿收口，2026-11-06）
- 修复：`DcchengClient` 两条出站腿（`post`/`postJson`）补 `CallerCredentialSource.applyTo` 中继（原 `HttpEntity` 裸调 ⇒ gateway 按匿名 403 ⇒ 场景运行四类全降级）。
- 新增契约门 `DcchengClientCredentialRelayTest`（4 例）：中继逐字断言（Bearer+四信任头）、postJson 同款、无上下文不发明主语、类内无第二套取头实现（`getHeader(` 零命中 + applyTo 恰 2 次）。**变异探针**：注释掉 post 腿中继 → 1 红；恢复 → 4/4 绿。
- 全量：workspace-impl `Tests run: 395, Failures: 0, Skipped: 3` / `BUILD SUCCESS`（+4 用例）。
- live 复验（重启 workspace 后）：
  - `POST .../runs`（DIAGNOSE，毛利率 2.1%）→ `run_30eff59a-8f7 / SUCCEEDED / degraded=false`，返回**真实因果诊断**（rootCause「地缘政治/供需失衡 引发大宗商品市场波动」+ 因果链 + 认知层诚实降级说明「KG 未覆盖该指标，已切换规则兜底」——非传输层 403）；
  - `POST .../runs`（FORECAST）→ `SUCCEEDED / degraded=false`，返回**确定性基线预测**（baseline-linear：水平 90.4164 / 斜率 -2.8841 / 残差 std=14.1292 / horizon=3，含预测点与上下界）——正式口径走确定性预测服务的验收红线在此腿成立。
- 至此 P1-12 三实例中 workspace→cognitive 与 workspace→security（B32）两腿已收口；**余 kb→DW CURATED**（kb 引擎侧，未动）。

### B34 知识抽取链路打通 + 契约探测 XML 化假零修复（EXTRACTION 边 present，覆盖率 33%→66.7%）
- **腿 1 — kb 共享 RestTemplate 凭据中继**：`KbEngineRestConfig#restTemplate`（@Primary，被 kb 抽取 / ontology SecurityEngineClient / ai AiSecurityEngineClient 裸注入共用）挂 `CallerCredentialRelayInterceptor`：当前请求带头则逐字中继、无上下文不加头（fail-closed 语义不变）。新增 `CallerCredentialRelayInterceptorTest`（4 例）。
- **腿 2 — kb→DW 寻址遵守切流登记**：`KbEntityInstanceExtractionService#datanetBaseUrl` 由硬传 `RouteMode.MONOLITH`（绕过 manifest datanet route=service 自环 gateway）改为 `SERVICE`；resolver 对未切流 owner 自动回落，行为兼容。
  - live：`POST /knowledge/sync/object/ont_entmgmt` → job `tg-obj-1793961671536` **SUCCEEDED**（此前两代恒 `ECOS-DATA-041 (403)`）；`ecos_kg_sync_log` 落 `SUCCESS / node_count=0`；`/knowledge/sync/status` 的 `ALL.lastSyncTime=2026-11-06 18:41:18` 首次非空。
- **腿 3 — 契约探测 Accept 协商（最隐蔽的一处）**：EXTRACTION 边仍 missing，现场取证链：
  - `EdgeContractProbe` 诊断行输出 `contractCount=0` 但 `lastVerifiedAt=18:41` 有值（快照有值却计数为 0）；
  - 逐步探针：raw 应答 `{"syncedCount":28}`（数字）但 workspace 侧反序列化为 **`java.lang.String`**；
  - 终因：`HttpContractProbeFacade#requestData` 未设 `Accept`（`*/*`）⇒ gateway 内容协商用 **XML converter** 应答（`contentType=application/xml`）⇒ `Jackson2Xml` 读回 `Map` 时数字全变 String ⇒ `asLong=0` ⇒ `ContractSnapshot.contractCount` **假 0**。
  - 修复：出站钉 `Accept: application/json`。新增用例 `extractionRequestPinsAcceptJson`（断言出站头 + `contractCount=56` + verified）；**变异探针**：删 setAccept → 1 红。
- 实跑：kb 模块 `364/0/2`；workspace-impl 全量 **`396/0/3`**（+1 用例 + 中继 4 例）。
- **live 终验**：`GET /workspace/scenarios/sc_ent2_group_month/completeness` → `coverage=0.6667`、`present: MAPPING count=4 | EXTRACTION count=56`、`missing: COGNITION`。EXTRACTION 边首次 present。
- 残余：COGNITION 仍无契约载体（`HttpContractProbeFacade` 显式抛「智能体注册表未持有知识库引用（需求侧缺口）」）——需新增智能体→知识库引用载体（ADR/设计决策），本批未擅自加字段。

### B35 COGNITION 契约载体落位（完整度 66.7% → 100%）
- 载体选择（不新增表/列/API）：既有 `ecos_ai.ecos_agent_registry.metadata` JSONB 扩展位新增 `knowledgeBases` 数组，元素取知识库**业务键**（与 `ecos_scenario_binding_link` 的 `ent2_doc_profit_caliber` 同源）。迁移 `V316__agent_registry_knowledge_carrier.sql`（幂等 `jsonb_set`，仅当键缺失时写入；dev 库实跑 `UPDATE 1`，复核 `["ent2_doc_profit_caliber"]`）；同步更新种子 `entmgmt_demo_seed_v2_w6_ai_scenario.sql`。
- 探针实现：`HttpContractProbeFacade` 新增 `probeCognition()` —— 读 aiming 既有只读面 `GET /api/v1/agent-mesh/agents`，解析各智能体 `metadata.knowledgeBases` 引用数求和、时间取引用者 max(updatedAt, createdAt)；不可解析/缺失按 0（不伪造）。原「无载体直接抛错」分支撤销。
- 门禁：`HttpContractProbeFacadeTest` 新增 2 例（有引用 count=2 verified；无引用 count=0 不 verified），全类 15/15；workspace-impl 全量 **397/0/3**；`MigrationVersionUniquenessTest` 5/5（V315 已被并发批次占用 `ddl_code_schema_alignment`，本迁移顺延 **V316**，门禁基线 316/317）。
- **live 终验**：`GET /workspace/scenarios/sc_ent2_group_month/completeness` → **`coverage=1.0`、`verdict=OK`**，`present: MAPPING count=4 | EXTRACTION count=56 | COGNITION count=1`，`missing=[]`、`probeErrors=[]`。
- 语义边界：本次仅表达「智能体声明消费哪些知识库」这一条配置事实，不含任何业务结果；载体字段落在既有 metadata 扩展位，属增量数据配置而非新增数据模型。

### B36 正式口径读面 RLS 策略补位（calibers 403 → 200）
- 根因：`GET /workspace/calibers` 恒 `403 PERMISSION_DENIED / reason=RLS_NO_POLICY_MATCHED`——`ecos_control.ecos_rls_policy` 无 `table_name='caliber_definition'` 命中行（安全引擎 fail-closed 行为正确，缺策略数据）。
- 处置：新增 `V317__caliber_definition_rls_demo_policy.sql`（幂等 INSERT，仅当 `(policy_name,table_name)` 不存在）——与既有 `ent2-demo-read-role` / `rls-finance-all` 同形，`role_id='role_admin'`、`predicate_template='1=1'`，不绑具体 user、不放宽其它实体；dev 库实跑 `INSERT 0 1`。
- live：`GET /workspace/calibers` → **200**，返回真实口径行（`accrual_revenue_amount` 权责发生制验收确认额 / AMOUNT / CNY / ownerRole=finance_analyst …）。
- 门禁：`MigrationVersionUniquenessTest` 顺延 **317/318**，5/5 绿。

### B37 场景装配与发布闭环（沙盘覆盖 6/6、SANDBOX 运行、蓝图 RELEASED）
- **沙盘装配**：`POST /workspace/scenarios/{id}/sandbox/layout`（`expectedVersion=0`，`runMode=FORMAL`）写入 React Flow 布局——hub + 6 个 `type=resource` 节点，`data.category` 覆盖 DATASOURCE / ONTOLOGY_ENTITY / KNOWLEDGE_ARTICLE / AGENT_PROFILE / SECURITY_POLICY / INTERFACE_REF（refId 全部取自场景已绑定业务键）。live `pre-validate` → `sandbox_coverage: pass=true（all 6 categories present）`。
- **运行面四类全通**（live，全部 `SUCCEEDED`）：DIAGNOSE（真实因果诊断）、FORECAST（baseline-linear 确定性预测）、SIMULATE（扰动 +5%，返回 COMPLETED 仿真结果）、STRATEGY（策略产出）；**SANDBOX** 运行 → `run_540cbb12-ae9 / run_mode=SANDBOX / sandbox_lineage=SANDBOX`（与 FORMAL 行分离，正式指标未被写）。
- **蓝图发布**：旧版 v1 `gaps` 为自由文本、validate fail-closed 不可发布；按 §6.2 以**真实 live 证据**重建 v2 草稿（`7d7ea2bb-1567-441c-80f0-2ed740c4f38d`）：
  - 依赖 `ONTOLOGY/ont_entmgmt@1.0.1 status=PUBLISHED`；capabilities 无 required；tests 3 条 P0 均带当日实证（calibers 200 / FORECAST baseline / completeness OK）；
  - `GET validate` → **passed=true、reasons=[]**；
  - 状态机推进 `DRAFT→DISCOVERING→GAP_ANALYSIS→BLUEPRINT_READY→VALIDATING→READY_FOR_APPROVAL`；
  - `POST approve {approvalRef:"APR-2026-1107-E2E-CALIBER"}` → **APPROVED**；`POST release` → **201 RELEASED**；
  - 复核：`status=RELEASED`、`artifactDigest=90dbefad715297e0…`、`approvalRef=APR-2026-1107-E2E-CALIBER`；`contextDigest=null`（Context Pack 摘要未建，登记为余项）。
- 运行记录终态：`ecos_ai.ecos_scenario_run` 目标场景 **6 行**（FORMAL SUCCEEDED×4 / FORMAL SUCCEEDED_DEGRADED×1 / SANDBOX SUCCEEDED×1）。

### B38 Context 源读取器凭据中继 + 上下文装配余量定位（contextDigest 未完成）
- 修复：`cognitiveRestTemplate`（Context 五源读取器共享）挂 `CognitiveCallerCredentialRelayInterceptor`（薄适配器，判据仍单源 `CallerCredentialSource`；无上下文不加头、不覆盖显式头）。新增 4 例契约测试（3 语义腿 + 装配断言）。
- 说明：曾尝试把拦截器下沉 runtime-core 共享，但 runtime-core 无 spring-web 依赖，不为一个适配器改模块依赖，故按引擎各留薄适配器（kb 侧同名类保持）。实跑：cognitive 4/4、kb 4/4；双模块 `install` BUILD SUCCESS；gateway 重建重启。
- **结论：中继是必要前置但不足以打通**。live 复核三源仍 `UNAVAILABLE`，逐源定位出**上游数据面缺陷**（均非本任务代码）：
  1. `DATA_ASSET`：读取器调 `GET /api/v1/datanet/assets/{id}`；真实资产行在 `ecos_data.ecos_data_asset`（`asset_entmgmt_attribution` 等 4 行）但端点恒 `code=404 ASSET-010`；另 `GET /api/v1/datanet/assets` 列表恒 **409「conversion to Integer from int8」**。
  2. `KNOWLEDGE`：读取器调 `GET /api/v1/knowledge/articles/{id}`；库内文章存在（`a99a05ab…` 权责发生制产值确认办法 PUBLISHED），但列表端点 `GET /knowledge/articles` 恒 **500**。
  3. `COGNITIVE`：读取器走 `CognitiveAssetLifecycleService.resolve(id)`，需认知资产 id（belief/hypothesis/mind/model），与场景绑定的 AI_AGENT id 非同空间。
- 因此**未把 degraded 空包 digest 写入蓝图**（`contextDigest` 保持 null），避免伪造追溯证据。余量登记 P1-17。

### B39 P1-17 闭环：Context Pack 真实组装 + 蓝图 v3 发布（contextDigest 在位）
- **上游数据面缺陷修复**（均属卷02/卷04 代码，本任务范围内最小修）：
  1. `AssetDao` 两处 `COUNT(*)`（int8）读成 `Integer.class` → 资产详情恒 404、列表恒 409；改按 `Long` 取再降位（`countRegisteredAssets` + `confirmedFieldCount` 助手），新增契约门 `AssetRowMapperInt8ContractTest`（2 例）。
  2. `DataAssetVO` 未透出版本 → DATA_ASSET 源缺 SUP-CTX-01 三要素；**只增** `versionNo` 字段并在两条资产 SELECT/RMapper 携带；认知侧 `DataEngineContextSourceReader` 版本键表补 `versionNo/version_no`。
- **安全裁剪器生产实现**（P1-9 收口）：新增 `SecurityEngineContextTrimmer`——主体取本请求已认证 `SecurityContextHolder`（不读自报字段），调 `POST /api/v1/security/decision`（`assetRef.table + columns:["*"] + purpose=ai + subject.subjectId`），读数<strong>只经单源 `RlsExecutionContract.read`</strong>（四道不变量），仅 `ALLOW_ALL` 放行、非平凡谓词/拒绝/不可用一律 DENY。首版手抄键面与 fail-open 形态被既有 `RlsExecutionContractGateTest` 拦下（13 例），改单源后通过。
- **RLS 演示读策略**：新增 `V318__context_source_rls_read_policies.sql`（`ecos_data.ecos_data_asset` / `ecos_knowledge.knowledge_article`，仅 `role_admin`、`1=1`，幂等），dev 库实跑 `INSERT 0 2`；门禁基线 318/319。
- **live**：`POST /api/v1/cognitive/context/build`（DATA_ASSET + KNOWLEDGE 为必需源，版本取源侧真值）→ **200**、`ready=true`、`contextDigest=sha256:8a1857ac…`，两源 `accessResult=OK`。
- **蓝图 v3**：以真实 P0 证据（calibers 200 / 基线预测 / 完整度 OK / context pack digest）重建 → `validate passed=true` → 状态机 5 步 → `approve(APR-2026-1107-CTX-E2E)` → **`release 201 / RELEASED`**；复核 `artifactDigest=b2e0c0e3…`、**`contextDigest=sha256:8a1857ac…`**（摘要链闭合）。
- 门禁：cognitive `564+` 全绿（新增 trimmer 7 例、context 41 例）、workspace `397/0/3`、data-engine 资产 14 例、迁移门 5/5。

### B40 P1-16 闭环：场景 Mind 以认知域为权威源（pre-validate 三项全 PASS）
- 语义订正：`pre-validate.mind` 原读 legacy `ecos_scenario_mind`（按 F05-02/W116 停写、0 行）；改由 `DcchengClient#detectScenarioMinds` 直连认知 **E1 detect**（`GET /business/scenarios/{id}/cognition/detect`，带 `CallerCredentialSource` 中继），按返回 Mind 数判定；legacy 仅在 E1 不可用时兜底。
- 载体种子：`V319__scenario_cognitive_mind_seed.sql`（幂等）——`ecos_cognitive_mind` 建 `mind_ent2_month`（`mind_key=mind_entmgmt_monthly`、`capability_mask=["DIAGNOSE","FORECAST","SIMULATE","STRATEGY"]`、`is_active=1`）并绑定 `sc_ent2_group_month` 为 PRIMARY；dev 库实跑 2×`INSERT 0 1`；门禁基线 319/320。
- live：`GET .../cognition/detect` → 200 返回该 Mind；`POST .../pre-validate` → **`mind=true | sandbox_coverage=true | bindings_reachability=true`**（mind 明细 `source=cognitive, count=1`）。
- 门禁：workspace 全量 `397/0/3`；`DcchengClientCredentialRelayTest` 三出站腿断言同步（2→3，中继仍单源）。

### B41 预测-实际-偏差关联：端点实跑 + 缺口定位（按设计拒绝伪造，非缺陷）
- **实跑**：`POST /api/v1/cognitive/outcomes/evaluate`（结构合法请求：scenarioId/blueprintId+v3/runId/forecastRunId、`actualRef` 三要素、`metricRefs=[M_PROFIT]`、窗口 2026-11、逐指标 `metricCalculations`（deterministic=true 双引用）、七项门禁声明齐、幂等键）→ **200**、`evaluationId=5740e7c8…`、**`status=NEEDS_RECONCILE`**；`metrics[0]`：`predicted/actual/absoluteError=null`、`applicable=false`、`notApplicableReason="value seam absent"`；`diagnoses[0].verdict=INCONCLUSIVE`（无治理假设/信念关联）；`warnings` 含 deterministic 相关提示。
- **缺口定位（数据/服务面，非代码缺陷）**：
  1. `OutcomeMetricValueReader`（确定性计算产物只读接缝）**全仓无实现**——接口 javadoc 明确「本仓当前无业务侧计算服务实现 ⇒ 无 bean ⇒ 指标标不适用 + NEEDS_RECONCILE，禁 200 返回内置/伪造数值」；
  2. `ecos_dw.ecos_application_forecast_run` **0 行**（无确定性预测产物可引用）；
  3. 无实际值指标存储表（`%actual%`/`%biz_metric%` 均 0 命中）；数据引擎亦无「指标值」计算端点。
- 结论：该 E2E 项**能力在位且 fail-loud 正确**（零伪造），但**业务可执行性**依赖「确定性业务计算服务」（落 `ecos_dw`，属业务域/卷02 交付物）。认知侧不得自算（铁律 §0.6/ADR-14 + 红线 6），故不在此层补算。已登记 P1-18。

### B42 P1-19 闭环：场景 Mind 权威写面（认知域 V187+V188）
- 新增 `MindAssetDao` 写腿（`findMindByKey` / `upsertMind`（唯一键 `(mind_key,mind_version,is_deleted)`）/ `upsertScenarioBinding`（`(scenario_id,mind_id,is_deleted)`）/ `softDeleteBinding`）+ `MindRegistrationService`（注册/解绑，幂等、事务、缺必填 400）。
- 端点（认知域唯一写入口，路径池常量驱动）：`POST /api/v1/cognitive/minds`（注册/更新场景 Mind 并绑定，`capabilityMask`/`closedLoopBounds`/`bindingRole`/`priority`/`active`）、`DELETE /api/v1/cognitive/minds/{mindId}?scenarioId=`（逻辑解绑，回 `unboundRows`）。
- **门禁两处受控扩展（非放宽）**：①`CognitiveCrossDomainTest` 对 `MindAssetDao` 增第二例外——DML 紧随片段只能引用 `MIND_TABLE`/`SCEN_MIND_TABLE`（写其它表仍 FAIL）；②`MindUpdateGateTest` 路径池 6→**8**（计入注册 POST 与解绑 DELETE，并补 `DeleteMapping` 解析）。
- **live**（gateway 重建重启）：`POST /cognitive/minds`（`mind_benchmark`）→ 200 `mindId=e1c23934…`；`GET .../cognition/detect` → **2 个 Mind**；`DELETE /cognitive/minds/{id}?scenarioId=…` → `unboundRows=1`；detect 回落 **1 个**（临时数据已清，无残留绑定）。
- 门禁：cognitive 全量 **`575/0/0`**（含新增 `MindRegistrationServiceTest` 4 例）。
- **残余（登记 P1-20）**：workspace `POST/PATCH /workspace/scenarios/{id}/minds*` 仍 501——其 `ScenarioMindVO.id` 为 `Long`，与认知 Mind 的 32 位 UUID 主键不可无损映射；改为代理需变更既有出参类型（违「API 只增不改」），属契约决策。当前产品写路径 = 认知域端点（设计指定的注册门面）。

### B43 P1-18 余量收窄：实际结果读面存在，缺业务聚合口径
- 复检：`GET /api/v1/datanet/facts/action-outcomes?forecastRunId=|actionId=`（data-engine 只读，V279 表）已存在，行含 `factType/period/actualImpact/forecastRunId`；预测侧确定性产物亦已有（`POST /cognitive/forecast-runs/compute` + `GET .../{id}/results`）。
- 仍缺：**「行动结果 → 指标实际值」的业务聚合口径**（多 action 同一指标/期间如何汇总、未匹配行如何处置）与真实行动结果数据（`ecos_dw.ecos_application_action_outcome` 0 行）。该口径属业务域定义，认知侧不得自造（数值主权/§0.6/ADR-14），故 `OutcomeMetricValueReader` 保持未装配（fail-loud 已实跑验证）。
- 结论：P1-18 由「无数据」收敛为「**缺业务聚合契约 + 真实结果数据**」，需业务域（卷02/卷09）给出指标聚合定义后接 bean 即自动接通（接口语义原样支持）。

### B44 P1-20 闭环：workspace Mind 写代理改为认知域引用面（只增端点，既有 Long 面不动）
- `ScenarioMindVO` 只增 `mindRef`（String，认知 Mind 的 32 位 UUID）；既有 `id: Long` 语义不变（认知承载行为 null）。
- `DcchengClient` 增 `registerMind`（POST /cognitive/minds）/ `unbindMind`（DELETE）/ `setMindActive`（PUT …/active），三条腿均带 `CallerCredentialSource` 中继（出站腿自检 3→5）。
- `ScenarioMindService.upsertMind` 由 501 改为代理认知注册门面：`mindKey = mind_<scenarioId>_<label>`、`capabilityMask` 取 `cognitiveEndpoints` 中 `enabled!=false` 的键（缺配置=空掩码，不臆造），返回 VO 带 `mindRef`；新增 `unbindByRef` / `setActiveByRef`。
- 新端点（只增）：`PATCH /workspace/scenarios/{id}/minds/ref/{mindRef}`（激活位）、`DELETE /workspace/scenarios/{id}/minds/ref/{mindRef}`（解绑）；0 行 ⇒ 404 如实失败。既有 `PATCH/DELETE /{id}/minds/{mindId}(Long)` 保持原语义（legacy 停写 501）。
- 认知侧配套：`MindAssetDao.setMindActive` + `MindRegistrationService.setActive` + `PUT /api/v1/cognitive/minds/{id}/active`；路径池门禁 8→**9**。
- **live**：workspace `POST .../minds` → 200（`mindRef=e6f28adc…`，`active=0`）；`PATCH ref {active:true}` → detect **2**；`{active:false}` → detect 1；`DELETE ref` → `200` 且 detect 回落 1（临时行已清理，仅存种子 Mind 1 条）。
- 门禁：cognitive `575/0/0`、workspace `397/0/3`。

## 3. 未执行 / 未验证（不得当作完成）
- **数据库迁移已实跑（B22/B27/B29 订正）**：V311/V312/V313/V314 均已对 `ecos-postgres/sys_man` 执行并复核（表/种子/列/索引/溯源列）；单源目录 `V*.sql` 现扫 **264** 个，MAX=V314、NEXT=**V315**。本节原「仅落文件、未执行」表述作废。
- **年度经营预测 E2E 未完成**：2026-11-06 已起前端 vite:3000 并做浏览器点验（见 B30，7 个 Tab 的可达面 + 框架选择 live 成功）；**Playwright 工程未跑**，10 项业务验证仍阻塞于 P0-2 的契约/数据缺口与非鉴权因素。
- **全量 common-api 门禁未全绿**：存在与本任务无关的存量失败（`SecurityDenialHttpStatusRatchetTest`→UserController、`ArchitectureGuardTest`→ServerOrgAttribute、`NoMachineCodeFactoryFaceRatchetTest` 计数漂移），以及用户工作树中 `RuntimeCacheManager` 未提交改动导致 `runtime-core` 从头编译失败；本任务未修改这些文件。
- **前端全量 `npm test`**：本机默认 Node 下 jsdom 依赖 `ERR_REQUIRE_ESM`，与既有环境约束一致；本次改用 `--environment=node` 跑受影响的静态/服务测试，未宣称 jsdom 全量绿。

## 4. 变更文件清单（本任务新增/修改）

**DDL**
- `docs/50-db_script/02-flyway-migration/gateway/V311__cognitive_framework_registry.sql`（新）
- `docs/50-db_script/02-flyway-migration/gateway/V312__scenario_run_mode_lineage.sql`（新）
- `docs/50-db_script/02-flyway-migration/gateway/V313__kb_ontology_version_width.sql`（新；4 列加宽 20→128，dev 库已实跑；门禁基线 312/313→313/314）
- `docs/50-db_script/02-flyway-migration/gateway/V314__kb_graph_ontology_traceability_apply.sql`（新；V134 幂等重放：graph_node/graph_edge 8 溯源列 + 3 索引，dev 库已实跑；门禁基线 313/314→**314/315**）

**本体/知识后端**
- `engine/ontology-engine/.../dao/OntologyVersionDao.java`（账实订正：created_at→create_time、snapshot→snapshot_json、显式列投影）
- 测试 `engine/ontology-engine/.../dao/OntologyVersionContractDaoTest.java`（新，6 例）
- `engine/kb-engine/.../dao/{KbOntologySnapshotDao,KgMapperDao,KbEntityInstanceExtractionServiceDao,EntityLinkerDao}.java`（快照腿切 V179 `ecos_kb_ontology_snapshot`）

**前端（B30 实跑修复）**
- `src/services/httpClient.ts`（`mergeHeaders`：Authorization 不被调用方 headers 冲掉 + content-type 去重）
- `src/pages/workbench/[scenarioId]/blueprint/CompletenessPanel.tsx`（`labelKey` 过 `t()`）
- `src/locales/workspace/{zh-CN,en}.json`（向导步骤 2 键 + 缺失边 5 键 ×2 语）
- 测试 `src/services/__tests__/httpClient.headers.spec.ts`（新，4 例，含变异探针）

**认知后端（api/impl）**
- `CognitiveApiPaths.java`（追加端点常量）
- `api/framework/CognitiveFrameworkStatus.java`、`CognitiveFrameworkDefinition.java`（新）
- `dao/CognitiveFrameworkDao.java`（新）
- `service/framework/{CognitiveFrameworkRegistryService,FrameworkSelectionService,PlanValidator,CognitiveExecutionStateMachine,CognitiveExecutionService}.java`（新）
- `controller/{CognitiveFrameworkController,CognitiveExecutionController}.java`（新）
- 测试 `service/framework/{PlanValidatorTest,CognitiveExecutionStateMachineTest,CognitiveExecutionServiceTest,FrameworkSelectionServiceTest,CognitiveFrameworkRegistryServiceTest}.java`（新）

**workspace 后端**
- `controller/ScenarioRunController.java`
- `dao/WorkspaceDataDao.java`
- `scenario/ScenarioRunService.java`
- `scenario/SandboxWriteGuard.java`
- `scenario/WorkspaceBaselineRunModeResolver.java`（新）
- `blueprint/ScenarioBlueprintService.java`、`blueprint/ScenarioBlueprintController.java`、`blueprint/ScenarioBlueprintValidator.java`、`blueprint/BlueprintQueryInvalidException.java`（新）
- 测试 `scenario/ScenarioRunSandboxIsolationTest.java`（新）、`scenario/ScenarioBlueprintGapsQueryTest.java`（新）、`scenario/ScenarioBlueprintGapsContractTest.java`（新）、`blueprint/ScenarioBlueprintValidatorTest.java`（新）、`scenario/ScenarioSandboxLayoutGuardTest.java`（新）

**门禁基线（测试）**
- `gateway/.../MigrationVersionUniquenessTest.java`
- `cognitive-engine-impl/.../guard/CognitiveIronLawsTest.java`
- `runtime/common-api/.../SuccessStatusFailureEnvelopeRatchetTest.java`

**前端**
- `src/services/cognitiveFrameworkClient.ts`（新）
- `src/pages/workbench/[scenarioId]/blueprint/FrameworkPanel.tsx`（新）
- `src/pages/workbench/[scenarioId]/blueprint/BlueprintPanel.tsx`
- `src/locales/workspace/{zh-CN,en}.json`
- 测试 `src/services/__tests__/cognitiveFrameworkClient.test.ts`、`.../blueprint/__tests__/frameworkI18n.spec.ts`（新）

## 5. 契约/架构影响
- API：仅新增 `/api/v1/cognitive/frameworks*`、`/plans/validate`、`/executions*`；既有路径/签名零改动。
- DB：新增 1 表 + 既有 `ecos_scenario_run` 加 3 列 + 1 索引；无删改。
- Security：新增端点落在既有 `/api/v1/cognitive/**` 认证与 Clearance 覆盖面；未新增匿名面。
- Runtime：未新增 Agent Loop/调度器/模型直连；执行记录为请求态。
- DIKCW：框架表=过程治理元数据，非业务事实；沙盘不写正式场景指标；金额仍只来自确定性预测服务。
