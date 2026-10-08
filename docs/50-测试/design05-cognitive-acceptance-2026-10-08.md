---
title: 详细设计-05 认知域落地验收记录（F05-01~17 / W115~W139→C97~C121 / DDL / 错误码 / B 章前端）
created: 2026-10-08
status: final-v1.0
owner: fullstack（实现与自检）／Reviewer·QA 复核待排
parent: ../30-设计/详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md
---

> 来源：《详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md》v1.2 定版落地批次（分支 `release/v2.1-alpha`）
> 日期：2026-10-08
> 责任人：fullstack 会话（实现 + 自检）；验收方 = PMO / Reviewer / QA
> 版本：v1.0（正式记录，非草稿）

## 〇、判定口径（先立规矩，再报结果）

1. **批准 ≠ 已实现 ≠ 已验收**。本文只登记「设计件冻结验收标识的实测状态」，设计件 §八 前言的这条约束对本文同等生效。
2. 验收标识一律取设计件原文（§二 F05-01~17 每项「验收」行、§8.2 接缝 5 项、§8.1 追溯矩阵「可执行验收标识」列），**未自造用例名**（Q13 反虚假验收防线）。
3. 凡未实跑的（真库行数比对、迁移执行、Playwright、界面人工点验）一律显式写 **未执行** 并附阻塞原因，不写"接近完成"。
4. surefire 输出为**类粒度**（`Tests run … -- in 类名`）。方法粒度证据由「冻结方法名在源码存在」+「该类全绿」两条组合给出，第 1 条的核验方式在第 1.3 节写明。

---

## 一、总账（实测命令与结果）

### 1.1 认知模块终验（已执行）

```bash
# 工具链由本机解析链提供（JAVA_HOME/MAVEN_HOME → PATH；覆盖点 _win_tasks/env.local.ps1，已 gitignore）
mvn -o -f ecos_backend/pom.xml \
    -pl engine/cognitive-engine/cognitive-engine-api,engine/cognitive-engine/cognitive-engine-impl \
    -am test
```

| 项 | 实测 |
|:--|:--|
| 结果 | `BUILD SUCCESS` |
| cognitive-engine-impl 用例数 | **`Tests run: 240, Failures: 0, Errors: 0, Skipped: 0`** |
| 认知测试类数 | 60 |
| 耗时 | 20:15 min（离线 `-o`） |
| 覆盖 | §二 F05-01~17 全部点名类 + §8.2 接缝类 + REQ-COG-04/05/06/07/09 门禁类 |

### 1.2 全 reactor Gate B（四轮：r2 失败 → 修复 → r3 续跑 → r4）

```bash
mvn -o -f ecos_backend/pom.xml install -DskipITs -Djacoco.skip=true          # r2
mvn -o -f ecos_backend/pom.xml install -DskipITs -Djacoco.skip=true \
    -rf :data-engine-impl                                                    # r3（修复后续跑）
mvn -o -f ecos_backend/pom.xml install -DskipITs -Djacoco.skip=true \
    -rf :gateway                                                             # r4（基线减项后续跑）
```

| 轮次 | 结果 | 说明 |
|:--|:--|:--|
| r2 | **BUILD FAILURE**（14:08 min，至第 22/50 模块） | 唯一失败 = `BusinessDomainWriteArchTest#noEcosDwOutsideDataEngine`（data-engine-impl，C.3.6 业务域写通道守卫）。此前 21 个模块（runtime 底座 / common / security / sysman 门面等）测试全绿 |
| 根因 | 设计件 §8.1 要求 C 层对象目录给出「物理载体或引用态二选一」，`CognitiveObjectClass.PREDICTION` 的物理载体即 data-engine 侧 `ecos_dw.ecos_application_forecast_run`；该字面量是**目录描述符**而非 SQL，被该守卫按字面量命中 |
| 修复 | 在该守卫内加**逐行**只读描述符豁免（命中行必须自带 `read-only-carrier-descriptor` 标记且文件在登记面内），并**新增反向守卫** `readOnlyCarrierExemptionStillLive`（防死豁免 / 防扩面：未带标记的 `ecos_dw` 行仍判 FAIL，登记文件失真即 FAIL）。未放宽原红线 |
| r3 | **BUILD FAILURE**（至最后一个模块 gateway，`ECOS Gateway FAILURE [02:47 min]`；模块 22~28 全 SUCCESS） | gateway 106 用例中**唯一失败** = `ConfigSingleSourceArchTest#baselineEntriesStillViolate`（该类另 1 用例绿）。详见第 1.4 节 |
| 根因 | 本批 F05-13 把 cognitive 侧 `dao/ConfigQueryDao.java` 切断到 sysman 配置门面并**删除该文件**；gateway `ConfigSingleSourceArchTest` 的反死豁免守卫（"已收口即删基线行"）据此正确报警 —— 属基线登记未同步，非红线放宽 |
| 修复 | 从 `KNOWN_VIOLATION_BASELINE` 移除该条已失效登记（并在 Javadoc 注明移除依据）。**未新增豁免、未放宽断言** |
| r4 | **BUILD SUCCESS**（`MVN_EXIT=0`，gateway `Tests run: 106, Failures: 0, Errors: 0, Skipped: 4`） | `-rf :gateway` 单模块续跑；`ConfigSingleSourceArchTest` 2/2 绿（减基线后主用例仍绿）；4 个 Skipped = 既有 `@Disabled` 真库依赖用例（`arch.ComposePublicSqlGateLintTest` / `arch.PublicSqlRegressionTest` / `f0209.ScoringContextHydrationTest` / `f0214.PublicSqlRegressionTest` 各 1），非本批引入，见第 4 节 U-15 |

### 1.3 冻结验收标识存在性核验（已执行，静态）

设计件 §二 + §8.1 + §8.2 里所有 `mvn -Dtest=类#方法` 形态的冻结标识，逐条按「类文件存在 + 方法名在该类源码出现」核验：

| 项 | 结果 |
|:--|:--|
| 核验条数 | **51**（覆盖 F05-01~16 全部 `类#方法` + §8.2 的 4 个 `类#方法` + REQ-COG-05 五守护 + REQ-COG-07 门禁类） |
| 缺失 | **0**（`NOFILE`/`NOMETHOD` 零命中） |

> 复跑口径：对每个 `类#方法`，先按 `class 类名` 定位 `.java`（排除 `target/`），再在该类文件内 grep 方法名。

### 1.4 全 reactor Gate B 终局判定

r3 = `-rf :data-engine-impl`（从 r2 失败模块起重续同一 reactor），日志内序号 `[i/29]`，**gateway 为最后一个模块**；r2 已绿的第 1~21 模块不再重跑 —— r3 覆盖 r2 未跑完的全部剩余模块。

| 项 | 实测 |
|:--|:--|
| r3 局部证据（已获得） | 模块 22~28（data-engine→aiming）**全部 SUCCESS**；`BusinessDomainWriteArchTest` 实测 `Tests run: 3, Failures: 0, Errors: 0`（含新增反向守卫）；`ModuleDependencyArchTest` **`Tests run: 16, Failures: 0`**（含 `cognitiveDoesNotWriteEcosDwDirectly`:1000 / `businessMustNotConstructRestTemplate`:493 / `businessMustNotSelfSchedule`:382）；gateway `RouteManifestParityTest` `3/0`；`engine.data.ArchitectureTest` `5/0`、`engine.ontology.ArchitectureTest` `5/0` |
| r3 唯一失败与处置 | `ConfigSingleSourceArchTest#baselineEntriesStillViolate` → 已按守卫自身口径删除失效基线行（见 1.2 修复行） |
| r4 结果 | **`BUILD SUCCESS` / `MVN_EXIT=0`**（实测行已捕获，非推断）。拼接判定：r2 模块 1~21 绿 + r3 模块 22~28 绿 + r4 gateway 绿 = **全 reactor Gate B 通过**（50 模块）；`[ERROR]` 行数 = 0 |
| Gate B 判定 | **通过**（唯一残留 = 4 个既有 `@Disabled` 真库用例，见 U-15；未因本批放宽任何守卫） |

### 1.5 前端（已执行）

| 命令 | 结果 |
|:--|:--|
| `npm run lint`（= `tsc --noEmit`） | **exit 0，零诊断** |
| `npx vitest run src/services/__tests__/auth.tokenSingleSource.spec.ts src/api/__tests__/cognitivePaths.singleSource.spec.ts src/services/__tests__/cognitiveClient.spec.ts src/pages/scenario/cognition/__tests__ src/pages/scenario/cognition/DetectTabStubPlaceholder.test.tsx` | **7 文件 / 35 用例全绿**（`Test Files 7 passed (7)`，`Tests 35 passed (35)`） |

同一命令里另有 **1 个 unhandled error**：`src/pages/scenario/CognitionIntervention.test.tsx`（PMO-59 P4b 既有件，需 jsdom 环境）worker 启动失败 ⇒ `ERR_REQUIRE_ESM`（`html-encoding-sniffer` require ESM-only `@exodus/bytes`，本机 node 20.18.1）。

- 定性 = **既有环境级破损**，非本批引入：本批对 `vite.config.ts` 只**新增** `src/**/*.spec.{ts,tsx}` 收集项，未改 `environment: 'jsdom'` 与既有 `.test.*` 收集面；受影响的是既有 13 个 `.test.*` 文件家族。
- 处置 = 本文第 4 节记 **受阻（环境）**；本批新增的认知域组件级用例一律走 `// @vitest-environment node` + `react-dom/server` 静态渲染（沿用仓内既有约定），因此可跑且已绿。

---

## 二、逐 F05 项落地与验收（§二 A 章 17 项，无漏项）

| F | 设计件冻结验收标识（原文） | 制品（本批） | 实测 |
|:--|:--|:--|:--|
| F05-01 契约单源 + 路由唯一 | `CognitiveContractSingleSourceTest#noInlineCognitivePathLiterals`；`RouteUniquenessTest#engineCognitivePrefixHasSingleOwner` / `#scenarioCognitionFourEndpointsAreAddressable` | `cognitive/api/CognitiveApiPaths`（含 `SCENARIO_COGNITION_TEMPLATE`）+ `ScenarioCognitionController`；路径字面量零散落 | 类绿：1 + 3 用例 ✅ |
| F05-02 Mind 资产落点与注册/绑定 | `CognitiveSchemaPreflightTest#mindTablesResolveBeforeE1`；`ScenarioCognitionIntegrationTest#e1ReturnsEmptyArrayWhenScenarioHasNoMind`；`ScenarioMindWritePathTest#noInsertIntoDroppedLegacyMindTable` | `ecos_cognitive.ecos_cognitive_mind` / `..._scenario_mind`（V190）+ `MindAssetDao` + 预检门禁；`kb_mind_registry` 禁名入面 | 类绿：1 + 12 + 1 ✅（DG-6「无 Mind → 200 `data:[]`，禁 404」由该用例锁定） |
| F05-03 E1 detect | `ScenarioCognitionIntegrationTest#e1DetectHappyPathFieldsAndActiveOnly` / `#e1Detect404WhenScenarioMissing` / `#e1Detect404WhenScenarioSoftDeleted` | `GET /api/v1/business/scenarios/{id}/cognition/detect`（新建，前后端原双零命中） | 同一类 12 用例内 ✅ |
| F05-04 E2 operation-eval | `CognitiveEvalCalculatorTest#scoreEqualsProbabilityWhenWeightIsOne` / `#topNTruncatesAtFiftyAndFlagsTruncated`；`ScenarioCognitionIntegrationTest#e2RejectsMindNotOnScenario` | `BeliefScorer`（确定性评分，weight=1 ⇒ score=probability）+ Top-N≤50 + `truncated` | 2 + 12 ✅ |
| F05-05 E3 hypotheses | `#e3StrictMindFilterNoFallback` / `#e3NeverReturnsSoftDeletedRows`；`HypothesisStatusMachineTest#onlyFourCanonicalStatusesAreWritable` | `HypothesisReader` 严格 mind 过滤（禁兜底）+ `HypothesisStatus` 四态单源 | 12 + 3 ✅ |
| F05-06 E4 beliefs | `#e4UnmatchedDomainKeepsRowWithNullCapability` / `#e4OrdersByUpdatedAtDesc` | `BeliefReader` capability 投影：交集空 ⇒ `capability=null` 且**不丢行**（DG-7） | 12 ✅ |
| F05-07 错误矩阵 / 503 穿透 | `CognitiveErrorMatrixIntegrationTest#{scenarioNotFound,scenarioSoftDeleted,mindNotOnScenario,mindNotFound,engineDisabled,schemaNotReady}`；`CognitiveNoEmptyFallbackTest#noCatchAllReturningEmptyCollection` | `CognitiveErrorAdvice`（guard / SQLException / Exception 三面）+ `CognitiveGuardException`；禁 200 空 body | **6 方法名一一对应**，类 6/6 绿 ✅；1 ✅ |
| F05-08 近似实现与死调用归一 | `CognitiveDeadCallAuditTest#everyFrontendCognitivePathHasBackendMapping`；`RouteUniquenessTest#cognitiveReplaySingleSignature` | 前端 `cognitiveClient` 路径集 × 后端 mapping 集做差集（差集必须为空）；replay 单签名 | 1 + 3 ✅ |
| F05-09 REQ-COG-02 因果诊断 | `CausalChainDeterminismTest#contributionIsRecomputableFromSnapshot`；`CognitiveIronLawsTest#diagnoseLeavesRowCountUnchanged`；`CausalChainEvidenceTest#unevidencedEdgeGoesToUnverifiedHypotheses`；`DiagnoseContractRegressionTest#legacyRequestSchemaUnchanged` | `RuleCausalService` / `CausalChainResult` 快照可复算 + 无证据边入 `unverifiedHypotheses`；旧请求 schema 未改（API 只增） | 1 / 4 / 1 / 1 ✅（**库侧行数比对该项记未执行**，见第 4 节） |
| F05-10 REQ-COG-03 反事实金额来源 | `ScenarioCounterfactualIntegrationTest#onlyOverriddenScopeDiffs` / `#contributionSumBalancesWithinCent`；`ExploratoryGuardTest#ragSimulatorOutputHasNoMonetaryFields` | `SimulationResult` DETERMINISTIC_DIFF（干预项外 diff=0、分项和 ±1 分）+ exploratory 通道无金额字段 | 2 + 1 ✅ |
| F05-11 capabilityMask 枚举映射 | `MindCapabilityMaskTest#enumMembersAreExactlyFive` / `#enumMatchesContractDocTable`；`MindCapabilityMaskCodecTest#roundTripThroughTextJsonArray`；`DomainAliasConfigTest#unknownDomainYieldsNullCapability` | `api/mind/MindCapabilityMask`（5 元冻结）+ `MindCapabilityMaskCodec`（TEXT-JSON 数组往返）+ 域别名表（合同文件 `capability-mask-contract.txt` 对齐） | 2 + 1 + 3 ✅ |
| F05-12 铁律守护接线 | `CognitiveIronLawsTest#diagnoseForecastSimulateLeaveRowCountUnchanged` / `#noCognitiveContextTable`；`CognitiveDependencyAuditTest#noRuleEngineOnClasspath`；`CognitiveNoLlmNumbersTest#chainsContributionScoreNeverFromLlmResponse` | 五守护接线（静态半区 + 门禁面）：不建表 / 不引规则引擎（pom 扫 drools/easy-rules/mvel，SpEL 允许）/ 禁 LLM 出数值（LLM 出口仅落 `explanation`/`candidate`）/ Context 不建表 | 4 / 2 / 1 ✅（**行数与 `to_regclass` 库侧实测记未执行**） |
| F05-13 三层纪律 + schema 限定 | `CognitiveLayerDisciplineTest#noJdbcTemplateInCognitiveServiceLayer`；`CognitiveSqlDialectTest#noJsonbCastNoIlNoBareReturning`；`SchemaDriftLintTest#cognitiveTablesAreSchemaQualified`；`CognitiveCrossDomainTest#cognitiveWritesNoOtherDomainTables` | service→DAO/Mapper 收口（0 Mapper → Mapper 层）；跨域写 `sysman.sys_config`/`kb.sys_compliance_rule` 消除（改走 sysman 配置门面 / kb REST） | 1 / 1 / 1 / 1 ✅ |
| F05-14 鉴权两态拉平 | `CognitiveAuthParityTest#sameEndpointsDenyInBothModes`（gateway/service 两态参数化）；`ScenarioCognitionIntegrationTest#e1ToE4RequireJwtAndClearance` | 四端点两态路由等价 + 默认 DENY + 租户过滤；`ECOS-COG-403` | 2 + 12 ✅ |
| F05-15 公共底座收口 | `ModuleDependencyArchTest#businessMustNotConstructRestTemplate` / `#businessMustNotSelfSchedule`；`CognitiveAuditFailClosedTest#auditSinkFailureRejectsAction`；`CognitiveLlmGatewayTest#noLlmCallOutsideGateway` | `CognitiveRestClientConfig`/`KbRestClientConfig` 经 runtime（`ServiceEndpointResolver` 两态寻址）；线程池/调度归 runtime-task；审计经 `EventBusService` 单通道且双失败拒收 | 2 + 1 ✅；`ModuleDependencyArchTest` 在 **gateway** 模块 —— r4 实测 **`Tests run: 16, Failures: 0`**（含 `#cognitiveDoesNotWriteEcosDwDirectly`）✅ |
| F05-16 stub 显式化 | `CognitiveStubHonestyTest#noLiteralUpInHealthPayload` / `#worldModelReturnsNotImplemented`；`CognitiveHealthProbeTest#eachComponentIsActuallyChecked` | 健康探针逐组件实检（禁 `"UP"` 字面量）；world-model/replay 等 ⇒ 501 `ECOS-COG-503` + `data.stub=true`（DG-9） | 7 + 4 ✅ |
| F05-17 前端重构（B 章） | `npx vitest run src/services/__tests__/cognitiveClient.spec.ts -t "authHeaders come only from services/auth"`；`npx vitest run src/api/__tests__/cognitivePaths.singleSource.spec.ts`；`tests/scenario-cognition.spec.ts › COG-IA 认知四件套唯一入口` | `services/cognitiveClient.ts` 单源（`SCENARIO_COGNITION_BASE` + `scenarioCognitionUrl()`，scenarioId URL 编码）+ token 只经 `getAuthToken()` | 前两条 **实测绿**（1 + 3 用例）✅；第三条 = Playwright，**未执行**（见第 4 节） |

---

## 三、承接章节（§八 接缝 / §五 REQ / D·C·E 章 / B 章）

### 3.1 §8.2 接缝验收 5 项

| # | 冻结标识 | 实测 |
|:--|:--|:--|
| 1 | `CognitiveObjectCatalogTest#everyCClassHasExactlyOneCarrierOrNone` | ✅ 类 2/2 绿（C 层 11 类对象：载体 XOR 引用态二选一、载体唯一、禁寄生 `ecos_knowledge`/`ecos_ai`） |
| 2 | `Cm07NumericGuardTest#llmTypeCannotWriteProbabilityOrAmount` | ✅ 1/1 绿（CM-07 只产 hypothesis，禁写 `belief.probability` 与金额/比率列） |
| 3 | `DecisionBasisOwnershipTest#decisionCaseHasSingleWritableCarrier` | 类 2/2 绿（静态半区）；**权威实测（`ecos_decision_case` 在 `public` 与 `ecos_ai` 双侧并存的写权威判定）记未执行** |
| 4 | `DeterministicForecastArtifactTest#resultsLandInEcosDwNotEngineSchema` | ✅ 类 3/3 绿（产物经 `POST /api/v1/datanet/write-channel` 落 `ecos_dw.ecos_application_forecast_run`(V197)，cognitive 禁直连） |
| 5 | `pw p13-cognitive-model-registry.spec.ts`（界面，载体归卷 08） | **未执行**：该 spec 文件不存在（前置件 P-3 界面卷归 08 册），设计件本身已预设此口径 |

另：只读解析端点 `CognitiveObjectCatalogController` 的可达性由 `CognitiveObjectCatalogEndpointTest` 覆盖（同批绿）。

### 3.2 REQ ↔ F ↔ 验收（§8.1 追溯矩阵 5 行主责全覆盖）

| REQ | F 项 | 冻结标识 | 实测 |
|:--|:--|:--|:--|
| REQ-COG-01 E1–E4 | F05-02/03/04/05/06/07/14 | `ScenarioCognitionIntegrationTest`（设计件注 6 法，实测该类 **12 法**，含点名 6 法）+ `CognitiveErrorMatrixIntegrationTest`（6 法）+ `tests/scenario-cognition.spec.ts › COG-E1/E2/E3/E4/503` | 两类 ✅；spec **未执行** |
| REQ-COG-02 因果诊断 | F05-09 | `CausalChainDeterminismTest, CausalChainEvidenceTest, DiagnoseContractRegressionTest` | ✅ |
| REQ-COG-03 反事实驱动 | F05-10 | `ScenarioCounterfactualIntegrationTest#onlyOverriddenScopeDiffs,#contributionSumBalancesWithinCent` + `ExploratoryGuardTest` | ✅ |
| REQ-COG-04 capabilityMask | F05-11 | `MindCapabilityMaskTest#…` + `DomainAliasConfigTest` | ✅ |
| REQ-COG-05 铁律守护 | F05-12 | 5 个 `CognitiveIronLawsTest#…` / `CognitiveDependencyAuditTest` / `CognitiveNoLlmNumbersTest`，并纳入 CI `pr-gate` | 类全绿 ✅；「纳入 CI `pr-gate`」**未执行**（本仓无 CI 流水线落点，门禁以 mvn 门禁类形态存在） |
| REQ-COG-06 C 层 11 类 | §8.1 接缝 | `CognitiveObjectCatalogTest` + `CognitiveObjectCatalogEndpointTest` | ✅ |
| REQ-COG-07 模型注册中心 | §五 | `CognitiveModelRegistryTest`（26 法）/ `Cm07NumericGuardTest` / `CognitiveModelTypeSingleSourceTest`（4 法） | ✅（`ModelPublishGateEvaluator` 四门真实裁决；regression 门因本仓无回归语料载体 ⇒ 恒 `PENDING` fail-closed，未假绿） |
| REQ-COG-08 Decision Basis 只读 | §8.1 | `DecisionBasisOwnershipTest` | 静态 ✅／权威实测未执行 |
| REQ-COG-09 确定性产物 | §8.1 | `DeterministicForecastArtifactTest` + `ForecastArtifactWriteChannel` | ✅ |

### 3.3 DDL 合规件（E.2/E.3/E.5）

| 件 | 内容 | 状态 |
|:--|:--|:--|
| `02-flyway-migration/gateway/V190__cognitive_belief_hypo_mind_binding.sql` | belief/hypothesis 绑定 + mind 资产两表 | 文件已落 |
| `V191__cognitive_converge_belief_hypo_evidence.sql` | 14 表收敛（正名 `ecos_cognitive.ecos_cognitive_*`）+ probability 前置 | 文件已落 |
| `V192~V196` | model/run-invalid/pipeline 登记、bizscenario 绑定、world-model registry、pairwise 对账视图、`nextval`→GUID 双写 | 文件已落 |
| `V197__ecos_dw_application_forecast_run.sql` | 确定性预测产物落业务域 APPLICATION 层 | 已在库基线 |
| `V198__migration_version_dedup_neutralize.sql` | 迁移版本号去重中和 | 文件已落 |
| `V199__cognitive_scenario_binding_target_columns.sql` | 场景绑定目标补列 | 文件已落 |
| `V261__cognitive_model_registry_cm_type_gates_immutable.sql` | CM 类型枚举 + 四门禁列 + 版本不可变 + CHECK | 文件已落 |
| `V262__cognitive_belief_probability.sql` | `belief.probability` 补列 | 文件已落 |
| `V263__cognitive_sys_config_seed.sql` | 配置种子（E.5 单源） | 文件已落 |
| `01-ddl-多方言/{mysql,postgresql}/07_ecos_cognitive.sql` | 多库方言镜像同步（E.5） | 已改 |

**实跑状态：未执行。** 依据设计件 E.2 括注与需求检视报告 §14.4 —— 本批次**只落迁移脚本文件，不对 `ecos-postgres` 执行 DDL**（未获授权项）。合规自检（MC01 VARCHAR(36) 应用侧 UUID / MC02 精度与 JSON→TEXT / MC03 无条件索引 / DR06~DR08 七基线列 / DR01 schema 限定 / 无 jsonb·无 `nextval` 主键生成·无裸表名·无 partial index）由 `SchemaDriftLintTest`、`CognitiveSqlDialectTest`、`db-migration-lint.ps1` 三处静态守卫，实测绿。

ST03-A 豁免登记：见 `../40-实现/列级加密豁免登记表-2026-09-28.md`（本批同步更新）。

### 3.4 错误码与降级矩阵（D.5 / C.6）

- **D.5 冻结 10 码**（`ECOS-COG-400 / 400M / 400P / 404 / 403 / 500 / 501 / 502 / 503 / 504`）+ **REQ-COG-07 扩展 4 码**（`400C / 404R / 409R / 422R`）= `CognitiveErrorCode` 常量 **14 枚**，每码配 `MSG_*` 冻结字面量（共 14 条）。
- 落地面：`CognitiveErrorAdvice`（`CognitiveGuardException` / `SQLException` / `Exception` 三面）。`e.getMessage()` 只入日志 + `traceId`，响应 message 一律取冻结字面量（F05-07 #4）；DG-9 附 `data.stub=true`；`data.traceId` 经 MDC 回显。
- DG-1~DG-10 对应实现点：DG-1 ⇒ `ECOS-COG-500` 503（禁 200 空 body）；DG-2 ⇒ `ECOS-COG-501`（message 与 DG-1 相异，可定位）；DG-3/4 ⇒ `ECOS-COG-404`（`scenario not found` / `scenario deleted` 区分）；DG-5 ⇒ `ECOS-COG-400`（禁静默兜底返回全部，`#e3StrictMindFilterNoFallback` 锁定）；DG-6 ⇒ 200 `data:[]`（禁 404）；DG-7 ⇒ 200 + `capability=null` 不丢行；DG-8 ⇒ `explanationUnavailable=true`，数值不因 LLM 不可用而缺失或伪造；DG-9 ⇒ 501 + `stub=true`；DG-10 ⇒ security 不可用默认 DENY（`ECOS-COG-403`，铁律 §2.4-6）。

### 3.5 C.7 可观测性 / C.5 两态 / E.5 配置单源

| 章 | 制品 | 实测 |
|:--|:--|:--|
| C.7-1 traceId | `X-Request-Id` → MDC → 响应 `data.traceId`（含错误态可复制） | advice 三面均挂载 ✅ |
| C.7-2 事件日志 | `api/observability/CognitiveEventLog` + `CognitiveObservability`（`EVT=CognitiveAx op/agent/scenario/mind/cost/result{ok|empty|deny|stub}` 四分） | `CognitiveEventLogTest` 绿 ✅ |
| C.7-3 审计 fail-closed | 诊断/模拟/配置写经 runtime-event `EventBusService`；`CognitiveAuditFallback` | `CognitiveAuditFailClosedTest` 2/2 ✅ |
| C.5 两态寻址 | `CognitiveRestClientConfig` / `KbRestClientConfig` 经 `ServiceEndpointResolver`（无自环 HTTP、无自建 RestTemplate 散落） | `KbRestClientConfigTest` + `CognitiveLlmGatewayTest` ✅ |
| E.5 配置单源 | `CognitiveConfigQueryService` 走 sysman 配置门面（单表分组），禁 `@Value` 散落默认值；V263 种子 | `CognitiveConfigSingleSourceTest` ✅ |
| D.3 契约 Schema | `CognitiveContractSchema`（diagnose `chains` / simulate `DETERMINISTIC_DIFF` / DG-8 标记） | `DiagnoseForecastContractTest` ✅ |

### 3.6 B 章前端（界面设计八要素 + B.0 归一 + B.9 工程约束）

| 项 | 制品 | 实测/状态 |
|:--|:--|:--|
| B.0 IA 三→一 | 新建 `pages/scenario/cognition/{CognitiveRightPanel,DetectTab,OperationEvalTab,HypothesesTab,BeliefsTab,CognitionStateViews}.tsx`（每 Tab 独立文件、≤800 行）；删死代码 `pages/aiworkbench/{CognitionView,CognitiveOperatingSystem}.tsx`、`aiworkbench/CognitionViewE2E.test.tsx`、`aiworkbench/cognition/barrel.ts`；因果图单源 `components/CausalGraphView.tsx` | tsc 零诊断（无悬挂 import）✅；`cognitivePaths.singleSource.spec.ts` 断言四 Tab 不得自行 `fetch(`/硬编码 `/cognition` ✅ |
| B.5 七态矩阵 | `CognitionStateViews` + `sevenStates.matrix.spec.tsx` | **15 用例绿**（加载/正常/空/故障/未启用/schema 未就绪/未实现；故障与空视觉可分） |
| B.6 密度分页 | E2 Top-N≤50 + `truncated`；E3/E4 `?limit=` 必传；30s 轮询 + debounce 100–300ms | 后端 `#topNTruncatesAtFiftyAndFlagsTruncated` ✅；前端参数化在 `cognitiveClient` |
| B.7 可访问性 | `themeTokens.aria.guard.spec.ts`（`aria-label`/`tablist`/`tab`/`tabpanel`/方向键 `onKeyDown`；三色置信度配文字档位） | **7 用例绿** |
| B.8 移动端 | 复用 `useMediaQuery`/`useMobileSidebar`/`MobileDataTable`；`<768px` 右栏抽屉；删手写 SVG sparkline | 组件层落地；**浏览器实测未执行** |
| B.9-1 `api.ts` 拆分 | 2,625 行 → `api.ts` 49 行兼容 re-export + 10 个域客户端（`agentClient/dataClient/dictConfigClient/iamClient/knowledgeClient/marketClient/monitorClient/ontologyClient/scenarioClient/securityClient`，全部 ≤800 行） | 行数实测 ✅ |
| B.9-2 auth 头单源 | `services/auth.ts#getAuthToken()` 三 key union；`httpClient.ts` 三处 ad-hoc `Bearer` 直读删除；`auth.tokenSingleSource.spec.ts` 全 src + 范围收口双门禁 | 4 用例绿 ✅。**设计件要求的 ESLint `no-restricted-syntax` 规则未落地** —— 本仓 `npm run lint` 入口是 `tsc --noEmit`，`ecos_frontend` 无 eslint 配置/依赖，等价守卫由上述 vitest 门禁承担（记为口径替代，非静默忽略） |
| B.9-3 i18n | 新建 5 命名空间：`causal.*`=27 / `hypo.*`=32 / `state.*`=19 / `model.*`=12 / `diagnosis.*`=23（zh/en 对齐）；`cognition.*`=32；删 `t(key,'中文fallback')` | `i18nKeys.exist.spec.ts` **3 用例绿**（断言所用键在两语言文件均存在）。**「补齐 141 个缺失键」这一存量计数未做全量核销**，实际口径以门禁（用到即必须存在）为准 |
| B.9-4 主题 token | 认知域范围（`pages/scenario/cognition`、`pages/aiworkbench/cognition`、`CausalGraphView.tsx`）禁硬编码 Tailwind 色，一律 `useTheme()` token | 门禁绿 ✅。**全仓 1,058 处存量硬编码色未收口**（跨域存量，属分册 08 / 前端规范 §十 范围），见第 4 节 |
| B.9-5 缓存与假 Git UI | `ScenarioManagementView.tsx` 的 `ecos_cached_git_commits/branches` localStorage 与假 push 动画删除；删 `project-workbench/tabs/GitVersionTab.tsx`；Git UI 只经 `services/gitService.ts` 且只传 `repositoryId` | `sevenStates.matrix.spec.tsx` 断言该文件不存在 + 无假 Git 引用 ✅ |
| B.9-6 stub 静默回落 | 认知域范围（`pages/scenario/cognition` + `cognitiveClient`）无 TODO/mock/本期 stub 残留 | 范围 grep 零命中（仅门禁用例文件自身提及）✅。**全仓 55 处/20 文件存量未核销**，见第 4 节 |
| B.9-7 E2E | `ecos-tests/tests/scenario-cognition.spec.ts` 新建 + `playwright.config.ts` 增 project 路由 | **文件已建，未执行**（需 7 JAR + 前端同跑，见第 4 节） |

### 3.7 F 章偏差回填与需求侧回写

- `docs/10-架构/架构设计说明书-2026-09-28.md`：ARCH_SPEC §十一 **C97~C121 全量回填**（本册设计件要求"完成即刻执行，不延后"）。
- `docs/20-需求/PRD-05-cognitive-engine需求规格-2026-09-28.md`、`PRD-08-场景工作台与前端需求规格-2026-09-28.md`：§7.1 契约/PRD 回写清单条目订正。
- `docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_{COGNITION,SERVICE}_DOC.md`：按 W117/C99 正名（`ecos_cognitive.ecos_cognitive_hypothesis|belief`，标注旧别名系 ADR-8 §5 禁用误名，只准作 R-12 映射输入）。
- 禁名门禁：`CognitiveObjectCatalogTest` 全仓扫描 **0 命中**；别名映射豁免（kb 侧纯别名 `case` 标签）加**反向核验**——含纯别名标签的文件必须声明 `*_REAL_TABLE` 真身常量且带 `ecos_cognitive.` 限定，否则 FAIL。

---

## 四、未执行 / 受阻清单（显式，不静默）

| # | 项 | 状态 | 阻塞原因 / 复跑条件 |
|:--|:--|:--|:--|
| U-1 | `ecos-tests/tests/scenario-cognition.spec.ts`（COG-E1/E2/E3/E4/503/越权/IA/stub/3 探索口径等） | **未执行** | Playwright 需 gateway:8080 + 6 service + workspace:18090 + 前端 :3000 同跑；本批次未拉起运行时。文件已入库，跑法 `cd ecos-tests && npx playwright test --project=app` |
| U-2 | `pw p13-cognitive-model-registry.spec.ts`（§8.2 第 5 项） | **未执行** | 该 spec **不存在**；界面载体归卷 08 册，前置件 P-3 未建。设计件 §八 前言已预设此口径 |
| U-3 | 迁移脚本实跑（V190~V196 / V198~V199 / V261~V263 + 多库镜像） | **未执行** | 需求检视报告 §14.4 未授权对 `ecos-postgres` 执行 DDL；本批次只落文件。执行前需备份 + `to_regclass` 预检 |
| U-4 | `CognitiveIronLawsTest#diagnoseForecastSimulateLeaveRowCountUnchanged` **库侧**行数前后比对 | **未执行（半区）** | 静态半区（禁 DML 面扫描）已绿；真库 count 比对随 U-3 迁移执行后才能做。该用例在类绿时打印「库侧未实测」提示，不伪报 |
| U-5 | `DecisionBasisOwnershipTest#decisionCaseHasSingleWritableCarrier` **权威实测** | **未执行（半区）** | `ecos_decision_case` 在 `public` 与 `ecos_ai` 双侧并存，写权威未经数据侧裁决实测；静态单写口断言已绿 |
| U-6 | `CognitiveIronLawsTest#noCognitiveContextTable` 的 `to_regclass(...)` 实值 | **未执行（半区）** | 同上，需真库；DDL 面已保证不建该表 |
| U-7 | REQ-COG-05 门禁「纳入 CI `pr-gate`」 | **未执行** | 本仓无 CI 流水线定义落点；门禁以 `mvn test` 门禁类形态存在，接入 CI 属工程设施另行项 |
| U-8 | 既有 13 个前端 `.test.*`（jsdom）用例，含 `src/pages/scenario/CognitionIntervention.test.tsx` | **受阻（环境）** | 本机 node 20.18.1 对 ESM-only `@exodus/bytes` 无 `require` 支持 ⇒ `ERR_REQUIRE_ESM`，worker 起不来。非本批引入（本批只新增 `.spec.*` 收集）；解法为升级 node ≥ 20.19/22 或改 jsdom 依赖链 |
| U-9 | B.9-4 全仓硬编码 Tailwind 色 1,058 处 / 19 文件 | **未完成（超本册范围）** | 本批只收口**认知域三处范围**并上门禁；其余属分册 08 / 前端开发规范 §十 收口任务 |
| U-10 | B.9-6 全仓 TODO/mock/stub 55 处 / 20 文件 | **未完成（超本册范围）** | 同上，本批只核销认知域范围 |
| U-11 | B.9-3「补齐 141 个缺失键」存量计数 | **未核销** | 改以「用到即存在」门禁保证（`i18nKeys.exist.spec.ts` 绿）；141 这个存量数未逐项对账 |
| U-12 | CM-06 相似度向量检索 / CM-04 结构方程与 do-calculus | **按裁决保持边界** | R-67 ② 已批准：CM-06 前置未解 ⇒ 暂不接入；CM-04 保持附件"逐步支持"口径。非本批欠账 |
| U-13 | REQ-COG-07 `regression` 发布门 | **fail-closed PENDING** | 本仓无回归语料载体 ⇒ 该门恒 `PENDING`，真实发布在语料设施落地前必 422。**未做假绿桩** |
| U-14 | 界面人工点验（4 主题 × 右栏 4 Tab 截图） | **未执行** | 需运行时拉起；本批只给静态门禁 + 组件级渲染证据 |
| U-15 | gateway 模块 4 个 `@Disabled` 真库依赖用例（`arch.ComposePublicSqlGateLintTest` / `arch.PublicSqlRegressionTest` / `f0209.ScoringContextHydrationTest` / `f0214.PublicSqlRegressionTest` 各 1） | **未执行（既有）** | r4 `Skipped: 4` 即此 4 项；均为设计-02/04 批次登记的存量真库门，本批未解除也未新增跳过 |

---

## 五、本批门禁/断言修正记录（7 条，逐条留痕；均不放宽红线）

| # | 现象 | 判定与修法 |
|:--|:--|:--|
| 1 | `DomainAliasConfigTest:43` 断言 `"plan"`，实现返回 `"PLAN"` | **实现正确**（capabilityMask 契约形态为大写，下游 `fromName` 严格等值），改**测试断言**为 `assertEquals("PLAN", r.resolve(" plan "))`，保留 trim + 大小写不敏感匹配语义 |
| 2 | `CognitiveObjectCatalogTest:112` 禁名门禁命中 kb 侧纯别名 `case` 标签与两份 40-实现文档 | 文档按 W117/C99 **正名**（不放行）+ 门禁内**整行窄豁免**（只豁免 `case "kb_cognitive_xxx":` 标签行）+ **反向核验**（豁免失去合法性即 FAIL）。未用文件名白名单，未拆字面量绕过正则 |
| 3 | `BusinessDomainWriteArchTest#noEcosDwOutsideDataEngine` FAIL（§1.2） | **逐行**只读描述符豁免 + 新增 `readOnlyCarrierExemptionStillLive` 反向守卫。原红线（非登记文件、未带标记行一律 FAIL）不变 |
| 4 | 前端两个全 src FS 扫描用例在全量并发跑中超 5s 默认超时（实测 6.2s） | 显式放宽到 30s 并写 WHY（CPU 争用，非断言失真）；单跑原本即绿 |
| 5 | `cognitivePaths.singleSource.spec.ts` 头注释含**我写入的不实交叉引用** | grep 证伪后改为可核验表述（同仓 `file-size-lint.test.ts` 亦声明 `@vitest-environment node`；ERR_REQUIRE_ESM 为本机实测事实）。教训：注释引用必须先核原文 |
| 6 | 新文档与 `.flattened-pom.xml` 出现本机绝对路径 | 文档内命令泛化为工具链解析链（`JAVA_HOME`/`MAVEN_HOME` → PATH，覆盖点 gitignored）；`.flattened-pom.xml` 构建后复原不入库 |
| 7 | r3 中 `ConfigSingleSourceArchTest#baselineEntriesStillViolate` FAIL（gateway） | 该守卫的**反死豁免**语义命中本批事实：`ConfigQueryDao.java` 已随 F05-13 收口删除 → 按守卫自述口径（"已收口即删基线行"）**减 1 条登记**。未新增豁免、未改断言、`no-new-violation` 主用例仍绿 |

---

## 六、门禁自检与结论

| 门禁 | 判定 | 依据 |
|:--|:--:|:--|
| 编号回填（C97~C121 → ARCH_SPEC §十一） | **完成** | 3.7 |
| DDL 合规静态自检 | **完成** | `SchemaDriftLintTest` / `CognitiveSqlDialectTest` / `db-migration-lint.ps1` |
| 迁移实跑 | **未执行** | U-3（授权缺位，非技术失败） |
| 后端认知模块测试 | **完成**（240/240） | 1.1 |
| 全 reactor Gate B | **通过**（r2 失败已修 → r3 失败已修 → r4 `BUILD SUCCESS`） | 1.2 / 1.4 |
| 前端类型 + 认知域用例 | **完成**（tsc 0 诊断；35/35） | 1.5 |
| 前端 jsdom 家族用例 | **受阻** | U-8 |
| Playwright E2E | **未执行** | U-1 / U-2 |
| 库侧铁律实测 | **未执行（半区）** | U-4 / U-5 / U-6 |
| S3 切流前置 5 项（C.5） | **代码面 5/5 满足**，切流本身未执行 | ①`ServiceEndpointResolver` 接线 ②aiming service 态鉴权拉平 ③两态等价测试绿 ④schema 预检门禁 ⑤审计 fail-closed |

**结论**：设计件 §二 A 章 F05-01~17 与 §五 REQ-COG-01~09、§三 B 章认知域范围、D.5 错误码、C.6 降级矩阵、C.7 可观测性、E.2 合规 DDL 的**代码/脚本/门禁制品已全部落地**；认知模块 240 用例与前端 35 用例实测通过，全 reactor Gate B 经 r2→r3→r4 三轮续跑后 **`BUILD SUCCESS`**（1.4），gateway 侧 `ModuleDependencyArchTest` 16/16 实测绿（含 F05-15 三门禁）；第 4 节 15 项**须真库 / 须运行时 / 须 CI 设施 / 超本册范围**的条目按"未执行"登记，**未以绿灯表述**。制品交付以本批 commit hash 为 DONE 凭证，未 push。

<!-- 详细设计-05 落地验收记录 / 2026-10-08 / v1.0 / fullstack / 口径：批准≠已实现≠已验收；未执行项全部显式 -->
