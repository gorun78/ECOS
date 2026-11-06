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

## 3. 未执行 / 未验证（不得当作完成）
- **数据库迁移已实跑（B22/B27/B29 订正）**：V311/V312/V313/V314 均已对 `ecos-postgres/sys_man` 执行并复核（表/种子/列/索引/溯源列）；单源目录 `V*.sql` 现扫 **264** 个，MAX=V314、NEXT=**V315**。本节原「仅落文件、未执行」表述作废。
- **未启动全栈 E2E**：2026-11-04 live 已覆盖 gateway:8080 + 5 service（18081/18082/18083/18084/18086）+ workspace:18090；**前端未起、Playwright 未跑**。年度经营预测 E2E 仍阻塞于 P0-2 的契约/数据缺口（非鉴权、非服务在线）。
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
