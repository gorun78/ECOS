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
| 53 | `cd ecos_backend/engine/kb-engine/kb-engine-impl && mvn -o test`（快照腿 4 个 DAO 改动后） | kb 引擎回归 | **PASS** | `Tests run: 343, Failures: 0, Errors: 0, Skipped: 2` / `BUILD SUCCESS` |
| 54 | 浏览器实跑 `#/workbench/sc_ent2_group_month`（vite:3000，admin 登录）逐 Tab 点验 | 前端六态与 i18n 实测 | **PASS（修复后）** | 修复前：向导第 3/4 步显示 `workspace.wiz.step.completeness/framework`、完整度面板显示 `workspace.completeness.missing.*`、框架选择恒 **403**（后暴露 **500 COG-504**）。修复后：5 个 Tab 均中文化；完整度显示「本体实体 → 知识资产（抽取）」「知识资产 → AI Agent（认知）」；框架选择渲染 `NEEDS_INPUT_OR_HUMAN` + `MISSING_INPUT:goal_statement_present` + 未校准声明；「认知摘要」空态文案由「未绑定任何数据或认知」订正为「暂无经营评估记录」（原文案与 6 类绑定并存，属事实性误导） |
| 55 | `npm run lint`（tsc --noEmit）+ `vitest run src/services/__tests__`（Node 22）+ `i18n-coverage` & workbench 用例 | 前端回归 + 新增头合并门禁 | **PASS** | tsc 零错；services **25 文件 / 347 测试**全过；i18n+workbench **3 文件 / 35 测试**全过。新增 `httpClient.headers.spec.ts` 4/4，变异探针撤合并后转红（1 failed） |
| 56 | live `GET /api/v1/workspace/scenarios/sc_ent2_group_month/actions`（页面「最近动作」） | 六态：依赖不可用应 503 不掩成 200 | **PASS（按契约 503）** | `503 COGNITIVE_UNAVAILABLE`，页面横幅 + `traceId=`；reason=「decision 底座网关未装配」。属卷07 已声明接缝（P1-14），非本批缺陷 |
| 57 | live `POST /api/v1/workspace/scenarios/sc_ent2_group_month/runs`（DIAGNOSE）→ `GET .../runs` | 场景运行链路首跑：状态机/降级/持久化 | **部分通过** | 运行落库 `run_e810c6a7-804 / FORMAL / SUCCEEDED_DEGRADED / degraded=true`，diagnosis 载「认知引擎暂不可用」（下游 403，P1-12 同源）；**但** `GET .../runs` 返回 `200 []`（库 1 行读不出）：`filterColumns` 因 `/security/decision` 恒 `403 无用户上下文` 按 DENY 置空（P1-15，UI「运行历史」同为空）。证据不伪装为可见 |
| 58 | 探针：`POST {gw,sysman}/security/decision` 三组（无 assetRef / 无 subject / subject.subjectId） | 裁决链断腿归因 | **PASS（归因坐实）** | 无 assetRef → 400；正确体无 subject → `403 ECOS-AUTH-010`（两宿主同）；`subject.subjectId=u-admin` → 200，返回 `rls.whereClause=(1=1)`、`columns.mode=allow`、`opa.policyState=EVALUATED_DENY` ⇒ 端点可用，断腿在 `ServiceContext` 填充（sysman 未注册 `HeaderAuthInterceptor`） |
| 59 | B32 修复后 live：`POST :18081/security/decision`（Bearer+信任头+assetRef，无 subject）→ `GET :18081/system/organizations/all` → `GET :18090/.../runs` → 浏览器场景页「运行历史」 | 信任链修复贯通（P1-15 方案①） | **PASS** | decision **200** 真实裁决束（rls `(1=1)`/columns allow/OPA EVALUATED_DENY）；organizations/all **200+机构列表**（修复前 code=-1）；runs **200+run 行**（修复前 200 空数组）；浏览器渲染运行历史表格（DIAGNOSE/毛利率 3.5%/SUCCEEDED_DEGRADED）。无信任头直连 → 403（fail-closed 保持） |
| 60 | 门禁：`SysmanHeaderAuthConfigTest`（3）+ `SysmanMapperScanContractTest`（3）+ sysman 模块全量 + workspace-impl 全量 + 前端 tsc/e6Placements | B32 变更回归 + 变异探针 | **PASS** | sysman `7/7`（变异探针：yml enabled=false → 1 红；回 HEAD 启动类 → 2 红）；workspace-impl `391/0/3`（端口字面量门禁触发后改逃生口方案）；前端 tsc 零错 + `2 文件 16` + `1 文件 4` 全绿 |
| 61 | B33 `DcchengClient` 凭据中继后 live：`POST .../runs` DIAGNOSE → `POST .../runs` FORECAST | 场景运行认知腿（原恒「认知引擎暂不可用」降级） | **PASS** | DIAGNOSE → `run_30eff59a-8f7 / SUCCEEDED / degraded=false`，真实因果诊断（rootCause「地缘政治/供需失衡 引发大宗商品市场波动」+ 因果链 + 认知层诚实降级说明「KG 未覆盖该指标，切换规则兜底」）；FORECAST → `SUCCEEDED / degraded=false`，确定性基线预测（baseline-linear：水平 90.4164 / 斜率 -2.8841 / horizon=3，含预测点与上下界）。新增 `DcchengClientCredentialRelayTest` 4/4（变异探针：撤中继 → 1 红）；workspace-impl 全量 `395/0/3` |
| 62 | B34 live：`POST /knowledge/sync/object/ont_entmgmt` → `GET /knowledge/sync/status` → `GET /workspace/scenarios/sc_ent2_group_month/completeness` | EXTRACTION 边（对象→知识资产契约） | **PASS** | 同步 job **SUCCEEDED**（此前恒 403）；`ecos_kg_sync_log` SUCCESS 行、`ALL.lastSyncTime` 非空；completeness `coverage=0.6667`、`present: MAPPING count=4 | EXTRACTION count=56`。根因链含一处隐蔽缺陷：探测出站未钉 `Accept` ⇒ gateway 以 XML 应答 ⇒ `Jackson2Xml` 把数字读成 String ⇒ `contractCount` 假 0（`contentType=application/xml` 现场日志 + 逐级探针坐实）；修法钉 `Accept: application/json`，新增用例并做变异探针 |
| 63 | B34 门禁：kb `CallerCredentialRelayInterceptorTest`（4）+ kb 全量 + workspace-impl 全量 | 回归 + 变异探针 | **PASS** | kb `364/0/2`；workspace-impl `396/0/3`；中继拦截器 3 条语义腿 + 装配断言；Accept 门 1 例（删 setAccept 转红） |
| 64 | B35 live：`GET /workspace/scenarios/sc_ent2_group_month/completeness`（V316 载体落库 + COGNITION 探针） | COGNITION 边契约载体 | **PASS** | `coverage=1.0 / verdict=OK`；`present: MAPPING count=4 | EXTRACTION count=56 | COGNITION count=1`；`missing=[]`、`probeErrors=[]`。门禁：facade 15/15（含 2 条 COGNITION 用例）、workspace 全量 `397/0/3`、迁移门 5/5（V316，基线 316/317） |
| 65 | B36 live：`GET /workspace/calibers`（V317 RLS 策略落库后） | 正式口径读面（原 403 RLS_NO_POLICY_MATCHED） | **PASS** | **200** + 真实口径行（`accrual_revenue_amount` 权责发生制验收确认额 / AMOUNT / CNY）；策略仅授 `role_admin`、未绑定具体账号；迁移门 5/5（V317，基线 317/318） |
| 66 | B37 live：沙盘布局保存 → `pre-validate` → 四类运行 + SANDBOX 运行 → 蓝图 v2 validate/approve/release | 场景装配、运行隔离、发布闭环 | **PASS** | `sandbox_coverage: pass=true（all 6 categories present）`、`bindings_reachability: pass=true（3/3 OK）`；DIAGNOSE/FORECAST/SIMULATE/STRATEGY 全 `SUCCEEDED`；SANDBOX 行 `run_mode=SANDBOX / sandbox_lineage=SANDBOX`；蓝图 v2 `validate passed=true → APPROVED → RELEASED(201)`，`artifactDigest=90dbefad…`、`approvalRef=APR-2026-1107-E2E-CALIBER`；`contextDigest` 仍 null（Context Pack 未建，余项） |
| 67 | B38：`cognitiveRestTemplate` 凭据中继 + Context Pack 三源 live 探测 | Context Pack 组装（`contextDigest` 前置） | **部分通过（阻塞上游）** | 中继 4/4（cognitive）＋4/4（kb），装配断言在位；live `POST /cognitive/context/build`：必需源仍 503D——`/datanet/assets/{id}` 对真实资产恒 `404 ASSET-010`、`/datanet/assets` 列表恒 `409 int8→Integer`、`/knowledge/articles` 恒 `500`、COGNITIVE 源 id 空间不同。**拒绝把 degraded 空包 digest 写入蓝图**（`contextDigest` 保持 null），登记 P1-17 |
| 68 | B39 门禁：`AssetRowMapperInt8ContractTest`（2）+ data-engine 资产用例（12）+ cognitive trimmer（7）+ context（41）+ 迁移门（V318，5/5） | 资产 int8 修复 + 安全裁剪器单源 + 策略补位 | **PASS** | 计数按 Long 取再降位；裁剪器只经 `RlsExecutionContract.read`，仅恒真放行（既有 `RlsExecutionContractGateTest` 13/13 守住键面与 fail-open 形态）；V318 `INSERT 0 2` |
| 69 | B39 live：`POST /api/v1/cognitive/context/build` → 蓝图 v3 建/validate/approve/release | Context Pack 真实组装 + 摘要链闭合 | **PASS** | pack `200 / ready=true / contextDigest=sha256:8a1857ac…`，DATA_ASSET（ver 1）与 KNOWLEDGE（ver 1792206510672）均 `OK`；v3 `validate passed=true → APPROVED → RELEASED(201)`，`artifactDigest=b2e0c0e3…`、`contextDigest` 非空 |
| 70 | B40 live：`GET .../cognition/detect` + `POST .../pre-validate`（V319 种子后） | 场景 Mind 就绪度（原恒 false） | **PASS** | detect 返回 1 个 Mind（`mind_ent2_month`）；pre-validate **三项全 PASS**：`mind=true（source=cognitive, count=1）`、`sandbox_coverage=true`、`bindings_reachability=true（3/3 OK）`；迁移门 5/5（V319，基线 319/320） |
| 71 | B41：`POST /api/v1/cognitive/outcomes/evaluate`（结构合法请求 + 七项门禁声明） | 预测-实际-偏差关联（E2E 第 10 项） | **部分通过（按设计 fail-loud）** | `200 / evaluationId=5740e7c8… / status=NEEDS_RECONCILE`；指标 `predicted=actual=absoluteError=null、applicable=false、reason=value seam absent`；`verdict=INCONCLUSIVE`。缺口：`OutcomeMetricValueReader` 无实现、`ecos_dw.ecos_application_forecast_run` 0 行、无实际值指标存储（登记 P1-18）；认知侧不自算（§0.6/ADR-14），不伪造通过 |
| 72 | B42 live：`POST /api/v1/cognitive/minds` → `GET .../cognition/detect` → `DELETE /api/v1/cognitive/minds/{id}?scenarioId=` | 场景 Mind 权威写面（V187+V188） | **PASS** | 注册 200 `mindId=e1c23934…`；detect **2 个 Mind**；解绑 200 `unboundRows=1`；detect 回落 **1 个**（临时数据清除，无残留）。门禁：cognitive 全量 `575/0/0`（+`MindRegistrationServiceTest` 4 例）；两处受控门禁扩展（跨域写白名单仅 Mind 两表常量、路径池 6→8 并支持 DELETE 解析） |
| 73 | B43：复检实际结果读面与确定性预测产物 | 预测-实际偏差关联的缺口收窄 | **部分通过（阻塞业务口径）** | 实际结果读面已存在（`GET /datanet/facts/action-outcomes?forecastRunId=`，0 行）；预测侧 `POST /cognitive/forecast-runs/compute` + `GET .../results` 在位；缺「行动结果→指标实际值」业务聚合口径与真实数据 ⇒ `OutcomeMetricValueReader` 保持未装配（fail-loud 已验证），登记 P1-18 |
| 74 | B44 live：workspace `POST .../minds` → `PATCH /minds/ref/{ref}` → `DELETE /minds/ref/{ref}` → 认知 E1 detect | Mind 写代理（原 501） | **PASS** | create 200（`mindRef=e6f28adc…`、`active=0`、`id=null` 只增语义）；激活后 detect **2**、停用后 1、解绑 200 后回落 1；临时行已清理。门禁：cognitive `575/0/0`、workspace `397/0/3`（路径池 9、中继腿 5） |
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
| 5 年度经营预测 E2E | 部分通过（2026-11-07 B34~B37） | 完整度 **1.0/OK**（三边 present）、正式口径 200、四类运行 SUCCEEDED、SANDBOX 运行隔离成立、蓝图 v2 **RELEASED**（artifactDigest 在位）；**余项**：无 active mind（设计停写）、contextDigest 未建、10 项中「预测-实际-偏差关联」未做真实实际值回填。详见 §3.4 |

> 口径：阶段 1/2/3 的“部分通过”仅表示所列子项已实跑；其未覆盖部分已在 P0/P1 登记，不计为通过。

### 3.4 年度经营预测 E2E live 取证（2026-11-04，判定：阻塞）

| 观测 | 实测结果 |
|---|---|
| 场景读取（workspace:18090 + Bearer + `X-ECOS-USER/ROLE/TENANT/DOMAIN`） | `GET /api/v1/workspace/scenarios/sc_ent2_group_month` → `200`；名称「集团月度经营分析（2026 年度）」、`status=ACTIVE` |
| 已绑定资产 | `bindings`：datasets `res_entmgmt_cur_attribution`、objectTypes `0e4e92d5…`、knowledgeBases `cf94fecc…`、aiAgents `ag_ent2_cognitive`、securityPolicies `abac_ent2_exec_read`、interfaces `if_ent2_forecast_list` |
| 链路完整性（后端权威） | ~~`coverage=0.3333 / PARTIAL`~~ → **2026-11-07 B34/B35 后 `coverage=1.0 / verdict=OK`**：`present: MAPPING count=4 | EXTRACTION count=56 | COGNITION count=1`，`missing=[]`、`probeErrors=[]` |
| 连边存在性（订正） | `GET .../graph` 返回 6 节点 + 5 边，含 `bsl_ent2_extraction_*`(obj→kb) 与 `bsl_ent2_cognition_*`(kb→ag) ⇒ **缺的不是连边**。`probeErrors` 仅 `COGNITION` 一条；`EXTRACTION` 无探测错误，卡在 `ContractSnapshot.isVerified()` 要求「`contractCount>0` **且** `lastVerifiedAt` 非空」，实测 kb `/knowledge/sync/status` 的 `syncedCount` 合计 28 但 `lastSyncTime` 全 `null` |
| 知识同步实跑（为补 `lastSyncTime`） | ① 起始：`POST /knowledge/sync/trigger` 与 `/sync/object/{id}` 均落 `failed`「读取本体快照失败」；`bc129d24…` 曾 500 `value too long varchar(20)`（已由 V313 修复）。② **B28**：本体版本发布原 500（`OntologyVersionDao` 读写 `created_at`/`snapshot::jsonb`），已修 → live `create/publish 200`。③ **B29**：快照腿切 V179 `ecos_kb_ontology_snapshot` + V314 补 8 溯源列 → `ont_entmgmt@1.0.1` 快照落盘（hash `c063657a…`），该次发布无 FAILED 行；`POST /knowledge/sync/object/ont_entmgmt` → `200 started`。④ **仍阻**：抽取 job `tg-obj-…` `FAILED / ECOS-DATA-041 …/data/layers/CURATED (403)`（同端点带 admin Token 200，属服务间凭据缺口 P1-12）；`sync/status.lastSyncTime` 仍全 `null`（P1-13）⇒ EXTRACTION 仍无法 present |
| 绑边接口负向校验 | 以资源 id（非 `sb_*` 绑定 id）调用 `POST .../binding-links` → `LINK-400: 绑定 … 不属于场景 sc_ent2_group_month`（服务端正确拒绝跨场景绑定；非缺陷） |
| 已有执行记录（2026-11-07 四次订正） | 目标场景 **6 行**：FORMAL `SUCCEEDED`×4（DIAGNOSE/FORECAST/SIMULATE/STRATEGY 各 1，均非降级）＋ FORMAL `SUCCEEDED_DEGRADED`×1（B32 前历史行）＋ **SANDBOX `SUCCEEDED`×1**（`run_540cbb12-ae9`，`run_mode=SANDBOX`、`sandbox_lineage=SANDBOX`）；全部可经 `GET .../runs` 读回 |
| 网关面差异（部署事实） | gateway :8080 承载 `/api/v1/business/scenarios/**`（blueprints 200），**不承载** `/api/v1/workspace/scenarios/**`（404）；场景族须直连 workspace:18090 并携带 Bearer + 四个信任头 |
| 正式口径读取（E2E 第 1~3 项前置） | ~~403 RLS_NO_POLICY_MATCHED~~ → **2026-11-07 B36 后 200**：`V317` 幂等补 `caliber-demo-read-role`（`table_name=caliber_definition`、`role_admin`、`1=1`），返回真实口径行（`accrual_revenue_amount` / AMOUNT / CNY / finance_analyst） |
| `COGNITION` 边契约载体 | ~~无载体、恒不可 present~~ → **2026-11-07 B35 载体落位**：既有 `ecos_ai.ecos_agent_registry.metadata.knowledgeBases`（`V316` 幂等写入 `ag_ent2_cognitive → ["ent2_doc_profit_caliber"]`），探针改读 aiming `GET /api/v1/agent-mesh/agents`；live `COGNITION count=1 / present` |
| 结论可追溯（E2E 第 10 项） | ~~v1 BLUEPRINT_READY、摘要链未产生~~ → **2026-11-07 B37 蓝图 v2 已发布**：`7d7ea2bb-1567-441c-80f0-2ed740c4f38d / status=RELEASED`、`artifactDigest=90dbefad715297e0…`、`approvalRef=APR-2026-1107-E2E-CALIBER`；`validate passed=true`（依赖 PUBLISHED、P0 三条均带当日 live 实证）。**余项**：`contextDigest=null`（Context Pack 未建） |
| 沙盘布局（E2E 第 9 项前置） | ~~默认布局未写库~~ → **2026-11-07 B37 已写入**（`expectedVersion=0`）：hub + 6 个 `type=resource` 节点，`data.category` 覆盖 DATASOURCE/ONTOLOGY_ENTITY/KNOWLEDGE_ARTICLE/AGENT_PROFILE/SECURITY_POLICY/INTERFACE_REF，refId 取自场景已绑定业务键 |
| 预校验（只读检查） | **2026-11-07 B40 后三项全 PASS**：`mind=true（source=cognitive, count=1；E1 detect 返回 mind_ent2_month）`、`sandbox_coverage=true（all 6 categories present）`、`bindings_reachability=true（覆盖 3/3，verdict=OK）` |

结论（2026-11-07 B34~B40 后订正）：运行链、裁决链、知识抽取链、正式口径读面、沙盘装配与隔离、蓝图发布**均已实跑通过**（完整度 1.0/OK、四类运行 SUCCEEDED、SANDBOX 行独立、v2 RELEASED 带 artifactDigest）。pre-validate 三项已全 PASS（含 Mind 与沙盘覆盖），**Context Pack 真实组装**（`contextDigest` 在位）且蓝图 v3 **RELEASED**（artifact+context 双摘要）。10 项业务验证**仅余 1 项**：「预测-实际-偏差关联」未做真实实际值回填（需业务实际值数据）。按任务书要求记录余量，不伪造通过。

## 4. 未通过/未执行汇总（不计为通过）

1. **年度经营预测 E2E（任务书第七阶段）**：2026-11-07 B34~B40 后，链路/运行/裁决/口径/沙盘/发布/上下文摘要全部实跑通过（完整度 1.0、四类运行 SUCCEEDED、SANDBOX 隔离、蓝图 v3 RELEASED、contextDigest 在位、pre-validate 三项全 PASS）；**仅余「预测-实际-偏差关联」一项**：端点已实跑且按设计 fail-loud（`NEEDS_RECONCILE` + value seam absent，零伪造），业务可执行性依赖「确定性业务计算服务」与真实实际值（P1-18），未伪造通过。
9. **EXTRACTION 边契约**：~~未 present~~ **2026-11-06 B34 已 present**（`contractCount=56`、`lastVerifiedAt` 非空）：知识同步 job 首次 SUCCEEDED（kb 共享 RestTemplate 凭据中继 + datanet 寻址遵守切流登记），并修掉探测 `Accept` 缺省导致 XML 应答把数字读成 String 的假 0。completeness 覆盖率 33%→**66.7%**。
13. **kb→DW 内部 REST 凭据缺口**：`POST /knowledge/sync/object/ont_entmgmt` 派生 job `FAILED / ECOS-DATA-041 …/engine/data/layers/CURATED (403)`；同端点 admin Token 200 ⇒ 需服务账号/Token 透传/mTLS 决策（P1-12），本任务未放宽鉴权。
14. ~~**安全裁决链断腿（P1-15）**~~ **2026-11-06 B32 已修复**：decision 对带信任头请求 200，运行历史/导出通道裁决面恢复（残余：workspace→cognitive 403 归 P1-12；sysman 切流登记待部署批）。
10. ~~**COGNITION 边契约**~~ **2026-11-07 B35 已 present**（V316 载体 + agent-mesh 探针）。余项同第 12 条（active mind 停写）。
11. ~~**正式口径读取**~~ **2026-11-07 B36 已 200**（V317 补 `caliber-demo-read-role`，仅 role_admin）。
12. **场景就绪度**：2026-11-07 B37 后 `sandbox_coverage=true`、`bindings_reachability=true（3/3 OK）`、SANDBOX 运行 1 行、蓝图 v2 RELEASED；**余 `mind=false`**（写面按 F05-02/W116 停写，V188 绑定表无写路径，需设计决策）。
2. ~~**数据库迁移实跑**~~：**2026-11-04 已授权永久执行**。V311/V312 落库并复核（表/种子 4/3 列/3 索引/默认 FORMAL）；另发现并处理既有漂移：V244 执行 + `ecos_ontology`/`ecos_control` 两表幂等补建（0 行）。
3. **全量 common-api 门禁**：存在存量失败与用户未提交改动引起的编译失败，本任务未触碰。
4. **前端全量 vitest（jsdom）**：默认 Node 下 `ERR_REQUIRE_ESM`，未宣称全量绿；已用 node 环境跑受影响测试。
5. **Context Builder 权限裁剪器**：五类 sourceType 读取面均已接线（DATA_ASSET/KNOWLEDGE/ONTOLOGY/CAPABILITY/COGNITIVE，B10）；但 `ContextSecurityTrimmer` 无生产实现，必需源在真实宿主中恒 403（fail-closed，P1-9）。
6. **认知方法**：已实现内置字典 + 框架 `methods[]` 关联 + 只读目录端点；未提供方法独立写 API（按 §1.1 方法不是独立可部署资产）。
7. **执行记录持久化**：请求态内存记录，未落 runtime-task；进程重启丢失（设计允许，但未做恢复验证）；已携带 `contextDigest` + `contextSources` 版本追溯链。
8. **执行记录恢复**：未做中断后恢复验证（依赖 P1-3 决策）。
