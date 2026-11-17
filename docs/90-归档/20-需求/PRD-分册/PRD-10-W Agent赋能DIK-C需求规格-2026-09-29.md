# PRD-10 W Agent 赋能 DIK/C 需求规格（分册 10）

> 来源: AI Agent（资深系统设计师视角） | 日期: 2026-09-29 | 责任人: AI Agent
> 版本: v1.3（**v1.3 2026-09-29 随需求检视报告 §十四 批量批准定版**：本册所涉 **R-31~R-50**（20 项）与承接的 **R-68~R-72**（5 项）**全部转已批准**，按各表「本设计推荐」列生效；ARCH_SPEC **Gate-1 已签字 ／ Gate-2 已通过**（`deliverable_allowed = true`，凭证 = 报告 §十四，依 **Q12** 不补分册 APPROVAL_RECORD）⇒ §六 两条挂靠行由"新立草案"改为"**已批准**（REQ-PLT-29 随 R-68/69/70、REQ-SEC-11 随 R-72）"、§八「状态」行与 §九 工期处置口径同步更正；**批准仅表示需求文本生效**，落地实测仍为 0，DDL 按 **R-64/R-37/R-38** 口径**只落脚本文件不实跑库**，改业务代码/新建 topic/实跑迁移/git 推送**需逐项再授权**（报告 §14.4）。v1.0→v1.1：§六 挂靠表与 PRD-01 v1.1 §3.2 的 REQ-PLT-24~28 编号对齐，并补 C257/C258/C259 三条实测发现的承接行。v1.1→v1.2：§六 依详细设计-10 §九「附件规范性内容回填」的实测结果**新增 3 条承接行**（事件底座契约与 topic 单源（→ **PRD-01 v1.3 REQ-PLT-29**，随 R-68/69/70）、模型管理员／认知模型负责人两角色（→ **PRD-07 v1.2 §5.6 REQ-SEC-11**，随 R-72 并与卷 09 R-66 并案）、测试体系 14 层 15 门槛（→ 既有 REQ-NF + R-48，场景包复用度量 4 项进卷 10 §8.3 门禁））；附件 §附录 A/B 的 CLI 首批范围**不另立行**，由 v1.1 既有的 **REQ-PLT-27／REQ-PLT-26** 承接行覆盖（结案前置 = R-50/P-5）；**本册不因此新立 REQ-WAG 编号**，待裁决草案 28→**30**、全量登记口径 136→**138**（v1.3 后：30 项草案已转正式、待裁决归零、全量 138 = 已批准基线））
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md) · [需求检视报告 §13.7](需求检视报告-2026-09-28.md) · [W-Agent 详细设计落地检视报告](W-Agent详细设计落地检视报告-2026-09-29.md)
> 外部输入: 《ECOS W Agent 详细设计》第一册（产品与业务流程）/ 第二册（技术架构）/ 第三册（数据模型·API·治理·验收），V1.1，2026-09-29（用户提供的附件，非本仓制品）
> 覆盖: REQ-WAG-01~24（W Agent 编排域）；跨域项按 §六 挂靠既有 PRD，不在本册重复立 REQ
> 模块: `ecos_backend/engine/ai-engine`（aiming:18084，**不新增 Maven 模块**，铁律 :292 基线 = 7 部署 JAR + 49 reactor）
> 术语前置: **W ≠ Agent**（铁律 v2.0 :63/:221 明文禁止 AI=W / LLM=W / Agent=W）。本册"W Agent" = 面向 W 域（Decision/Strategy/Policy/Action/Execution）的 Agent 族群之**编排主体**代号 → **R-31**

---

## 〇、本册定位与判定基线

| 项 | 判定 |
|:--|:--|
| 附件可否整体采纳 | ❌ **语义层可采纳，落位层必须重写**（附件前提 A-01/A-02/A-08 与 ECOS 规则或代码事实冲突，见检视报告 §四 WC-01/03/07） |
| ECOS 承载主体 | ai-engine（W 域 Owner）+ 五 Engine 的既有 API + runtime 器（access/task/monitor/event/llm-gateway） |
| 新增部署单元 / 容器 | **0**（附件二册 §1.5 的六模块与 model-gateway 骨架不采纳 → **R-37**） |
| 数据落位 | `ecos_ai`（Agent 控制面）/ `ecos_cognitive`（认知模型，待 **R-42**）/ 计算产物落业务域 `ecos_dw`（ADR-14 + 铁律 §0.6）；**不建 `agt_/aim_/cog_` 三库** → **R-33** |
| 验收口径 | 本册所有验收标识为 `mvn -Dtest=类#方法` 或 `*.spec.ts` 用例名；**前置件 P-1/P-2/P-3/P-4 未闭环前，凡依赖它们的标识一律记"未执行"，禁止记"通过"**（Q13 + 铁律 :14 → **R-48**） |

---

## 一、语义、边界与数据合规（REQ-WAG-01~04）

### 一.1 REQ-WAG-01 术语与代号辨析（P0，规则冲突项）

**陈述**：需求与设计文档中"W"一律指 W 域（决策与行动），"Agent"指编排主体；禁止把 W 定义为 Decision Engine 之外的任何单一组件，禁止把 LLM/AI/Agent 等同于 W。附件一册 A-01"W=Decision Engine"的解读在本仓作废，改写为"W 域由 ai-engine 承载，Decision 对象属 W"。

**附件锚点**：一册 A-01、二册 §1.3、三册 §10 边界总表。
**规则锚点**：铁律 v2.0 :63/:221/:235/:253。
**已批准（§十四 · R-31 推荐项 ①）**：术语定版 **W ≠ Agent**，保留 "W Agent" 代号 + 显式辨析。
**验收标识**：`mvn -Dtest=TerminologyArchTest#wDomainNeverAliasedToAgent`（对 `docs/**` 与 `*.java` 注释做"Agent=W / LLM=W"字面断言扫描；文档类断言由 `docs-lint` 脚本承担，任务 #29）。

### 一.2 REQ-WAG-02 Engine 同构映射与 W 域属主（P0）

**陈述**：附件的"五大 Engine"必须映射为 ECOS 实况"六引擎 + 四转化 + runtime 器 + workspace 场景层"，且决策对象属主唯一：

| 附件对象 | ECOS 承载 | 属主裁定 |
|:--|:--|:--|
| Data / Ontology / Knowledge / Cognition Engine | data-engine:18082 / ontology-engine:18083 / kb-engine:18086 / cognitive-engine:18089 | 不变（卷 02/03/04/05 已定） |
| Decision Engine（W） | **ai-engine:18084**（铁律 :235 "Decision Action Orchestration"） | **W 域 Owner = ai-engine**（新增 Decision/Option/Action 对象承接附件 §8/§9） |
| cognitive 既有 `ecos_decision*`（V103/V151） | 保留，定性为附件 §16.2 的 **Decision Basis（属 C）** | 不改表、不改 API（只增不改） |
| security / runtime | 横切，不属于任何 Engine 的私有能力 | 卷 00/01 已定 |

**已批准（§十四 · R-32 推荐项 ①）**：Decision/Option/Action 由 ai-engine 新增对象承接（只增不改）。
**验收标识**：`mvn -Dtest=ModuleDependencyArchTest#wDomainObjectsOnlyInAiEngine`（复用既有 ArchUnit 宇宙，补齐 cognitive↔ai 的决策对象归属规则，与 C71/C116/C143"ArchUnit 盲区"同批）。

### 一.3 REQ-WAG-03 Agent 编排三红线（P0，附件与规则同向）

1. Agent 及其专业子体**不得直连任何 Engine 库表**，能力一律经 Engine REST/api 门面（ST09 + IR01/IR06 + 二册 §10.3）。
2. 基础设施（LLM / 存储 / 调度 / 监控 / 事件 / Git）一律经 runtime 底座（铁律 §2.5 八条），**Agent 侧不得自建**（附件二册 §11.2~§11.4 与此一致）。
3. 场景层只编排不生产；Agent 编排域不承接场景语义（铁律 §0.6 + Q3 裁决）。

**验收标识**：`mvn -Dtest=ModuleDependencyArchTest#agentPackageNeverImportsEngineImplOrJdbc`、`mvn -Dtest=NoDirectEngineTableAccessTest#wAgentMappersAbsent`（沿用卷 06 的 mapper 排除扫描机制，不新立门禁）。

### 一.4 REQ-WAG-04 控制面数据合规（P0，红线项）

**陈述**：Agent 控制面表必须满足三条既有红线，附件 DDL 形态**不得原样采纳**：

| 子项 | 要求 | 违例来源（附件） | 裁决 |
|:--|:--|:--|:--|
| 落位 | 只落 ST07 五枚举：Agent/能力/候选/证据/决策/行动/审计 → `ecos_ai`；认知模型 → `ecos_cognitive`（待 R-42）；**禁 `CREATE SCHEMA`、禁"Agent 控制面库"** | 三册 §1.1/§1.2/附录 A | **R-33** |
| 形态 | 主键 `VARCHAR(36)` 应用侧 UUID 且 DDL 无默认值（MC01）；受控 JSON 只准 `TEXT` 且不得参与 WHERE/JOIN/索引（MC02）；金额 `NUMERIC(p,s)` 且逐列登记 ST03-A；表名必须 schema 限定（DR01）；`tenant_id`/`org_id`/`row_version`/七基线列齐备（DR06~DR08） | `text` PK+ULID、jsonb 参与唯一约束/索引、partial index、`PARTITION BY RANGE`、`DEFAULT now()` | **R-35** |
| 安全 | 租户/列级过滤继续走 security-engine RLS/CLS REST（ST04/ST05），**附件 §12.3 的原生 `CREATE POLICY` 代码块整体作废**；小样本（分组 <5）聚合保护登记为 security-engine 策略项（回指 ST03-A ②），不建第二套判定 | 三册 §12.3/§12.4 | R-35 / Z-7 |

**多租户口径**：`AM-17（跨租户读写为 0）` 的前置是平台级 REQ-PLT-24/25（控制域 tenant/org/row_version 补齐 + security RLS 贯通 `ecos_ai`）——实测单源 125 个迁移脚本仅 13 个含 `tenant_id`，Agent 侧单独补列**不足以**宣告达标 → **R-36**。本册只要求**新表齐列**，跨租户断言挂在 REQ-PLT-25 验收下。
**存量**：`ecos_agent` schema（V50，11 表）与 `services/agent-service`（不在 7 部署 JAR）只定性不迁 → **R-34 / R-41**。
**验收标识**：`_win_tasks/db-migration-lint.ps1`（新增"W-Agent 表 MC01/MC02/DR01/齐列"检查项，FAIL 数 = 0）+ `mvn -Dtest=SchemaOwnershipArchTest#agentControlTablesOnlyInFiveEngineSchemas`。

---

## 二、目标—问题链（REQ-WAG-05~07）

### 二.1 REQ-WAG-05 对象链与意图识别（P0）

`Goal → Mission → Business Question → Run → Step` 五层对象链必须在控制面可追溯；意图八类封闭枚举 `FORECAST / DIAGNOSE / SIMULATE / RANK / MONITOR / BUILD / DECIDE / REVIEW`；**`intent_confidence < 0.7` 的 Question 不得创建 Run**（应用层校验；附件的"触发器双重校验"因 DB 侧禁业务触发器纪律改为应用层 + 单测断言，DB 侧仅加 `CHECK` 约束）。

**附件锚点**：一册 §1/§3、三册 §2.1/§2.2。
**验收标识**：`mvn -Dtest=IntentClassifierServiceTest#belowThresholdRejectsRunCreation`、`mvn -Dtest=BusinessQuestionServiceTest#createRunRejectedWhenConfidenceIs069`。

### 二.2 REQ-WAG-06 槽位来源标注与澄清配额（P1）

每个槽位必须标注来源（用户明示 / 上下文继承 / 租户默认 / Agent 推断），**单轮澄清 ≤3 项**，推断槽位必须在回答卡上可视可改。推断来源与用户明示在审计中区分记录。
**附件锚点**：一册 §3、三册 §2.1 `slots_source`。
**验收标识**：`mvn -Dtest=SlotFillingServiceTest#clarificationRoundCappedAtThree`、`pw agent-goal.spec.ts > "槽位来源徽标可切换且推断项标注为 AGENT_INFERRED"`。

### 二.3 REQ-WAG-07 口径与版本变更链（P0）

修改 `caliber_version` / `scope` / `time_range` 必须**新建 Goal 版本**并以 `supersedes_*` 关联；已关联 Run 的 Goal **禁止原地修改**；口径变更产生的新 Run 以 `previous_run_id` 指向前序 Run（附件 二册 §3.5）。重评产生的 Readiness 记录只追加、旧记录 `superseded_by` 指向新记录，被决策引用的评估记录标 `pinned` 不受重评影响。
**验收标识**：`mvn -Dtest=BusinessGoalServiceTest#caliberChangeCreatesNewVersionNotInPlaceEdit`、`mvn -Dtest=ReadinessServiceTest#pinnedAssessmentSurvivesReassessment`。

---

## 三、DIKC Readiness（REQ-WAG-08~10）

### 三.1 REQ-WAG-08 四度评估与定级（P0）

按 D/I/K/C 四层各做覆盖度·及时度·完整度·一致度检查，产出等级 **A/B/C/D** 与逐层 `layer_summary`。**`grade` 由 Readiness 服务按阈值规则计算，禁止由 LLM 写入**（LLM 只可生成 `description`/`impact` 文字）；阈值来自租户配置（单表分组 `config_group`/`subsystem`，经 sysman api 门面，禁引擎自建配置表）。

**附件锚点**：一册 §2、二册 §6、三册 §3.1/§3.2。
**ECOS 落位**：评估对象落 `ecos_ai`；四层探测经各 Engine 只读端点（data 覆盖率 / ontology 口径 / kb 画像样本 / cognitive 模型可用），**探测失败必须降级为 `unknown` 并计入缺项，禁算 pass**（与卷 07 C148"空场景 100%"同类缺陷预防）。
**验收标识**：`mvn -Dtest=ReadinessGraderTest#gradeIsDeterministicFromThresholdsNotLlm`、`mvn -Dtest=ReadinessProbeTest#probeErrorYieldsUnknownNotPass`。

### 三.2 REQ-WAG-09 缺口、解锁值与补齐循环（P1）

每个 blocker/high 缺口产出 `suggested_action{type: fetch|task|confirm|approve}` + `unlock_value{grade_from, grade_to}`；补齐后复评，**循环上限 3 轮**，超限输出当前缺口等待用户（预算表 二册 §2.6）。
**验收标识**：`mvn -Dtest=ReadinessGapServiceTest#refillLoopCapsAtThreeRounds`、`pw agent-readiness.spec.ts > "缺口卡显示解锁值并可一键发起补齐子 Run"`。

### 三.3 REQ-WAG-10 预登记降级白名单（P0）

只允许使用**预登记**降级：附件的 `DG-K1 / DG-D1 / DG-C1 / DG-L1 / DG-C2 / DG-T1 / DG-M1 / DG-M2` 在 ECOS 侧逐条映射为策略枚举并登记于配置单源；**Run 与结论必须携带 `degradations[]` 标注且界面可见**；未登记的降级方式一律拒绝（附件 AC-05/AC-26）。
**验收标识**：`mvn -Dtest=DegradePolicyTest#unregisteredDegradeIsRejected`、`mvn -Dtest=RunResultVOTest#degradeAnnotationPropagatesToUi`。

---

## 四、运行编排（REQ-WAG-11~16）

### 四.1 REQ-WAG-11 Run 状态机与预算保护（P0）

状态机 `created → planning → plan_validated → executing ⇄ awaiting_input / awaiting_approval / waiting_task → completed | completed_degraded | failed | cancelled | timed_out`，迁移表冻结；预算默认：单 Run ≤60 Step、≤40 LLM 调用、同步 60s / 异步 30min、重规划 ≤3 次、补齐 ≤3 轮，超限动作按附件 §2.6。**Orchestrator 无本地状态**，崩溃后由任意实例从 Run Store 恢复（继续最后完成 Step 之后）。幂等：`(tenant_id, initiated_by, idempotency_key)` 唯一，重复提交返回同一 Run。
**已批准（§十四 · R-39 推荐项 ① A 单一自建）**：Run Store 属主随单一 Agent Runtime。
**验收标识**：`mvn -Dtest=RunStateMachineTest#illegalTransitionRejected`、`mvn -Dtest=RunIdempotencyTest#duplicateKeyReturnsSameRun`、`mvn -Dtest=RunBudgetTest#stepCountOver60Terminates`、`mvn -Dtest=OrchestratorRecoveryTest#resumesFromLastCompletedStepAfterCrash`。

### 四.2 REQ-WAG-12 Plan DAG、Playbook 优先与七项校验（P0）

Plan = Step 节点 + 依赖边的 DAG；**Playbook 模板优先，LLM 仅补参数与非关键分支**（附件 D-02）；执行前七项校验全过才允许执行：结构（无环/键唯一/依赖存在）、工具（存在 + 版本 `active`）、契约（输入引用类型兼容）、权限（Policy Guard 预检）、等级（不超工具与租户上限）、预算、写操作必须走 Candidate/Commit 通道。重规划必须重跑校验并在时间线标注。
**验收标识**：`mvn -Dtest=PlanValidatorTest#sevenChecksEachRejectIndependently`（参数化 7 用例）、`mvn -Dtest=PlanValidatorTest#writeStepWithoutCandidateChannelFails`。

### 四.3 REQ-WAG-13 探索规划与强制人工确认（P0）

无匹配 Playbook 时进入探索规划：LLM 只能从 Tool Registry 选工具组成 DAG，**生成的 Plan 必须人工确认后才执行**，并记录"缺少 Playbook"事件供产品沉淀（附件 二册 §3.2）。
**验收标识**：`mvn -Dtest=ExplorationPlannerTest#planRequiresHumanGateBeforeExecution`、`mvn -Dtest=PlaybookGapEventTest#missingPlaybookEventEmitted`。

### 四.4 REQ-WAG-14 Step 承流：引用 + 哈希 + attempt（P0）

Step 六类型 `tool_call | llm_reason | human_gate | wait_task | sub_run | aggregate`；`input_ref/output_ref` **只存引用不含明细**；`input_hash/output_hash` 支撑回放一致性核对；重试写新 `attempt` 不覆盖；`step.automation_level ≤ run.automation_ceiling`（L3 走审批凭证例外）；同一 `(run_id, step_key, attempt)` 唯一。子 Run 深度 ≤2、权限继承发起人 `on_behalf_of`、消耗计入父 Run、只回传结构化结果与 Evidence 引用。
**验收标识**：`mvn -Dtest=StepRecorderTest#stepPayloadContainsRefsOnlyNoDetail`、`mvn -Dtest=StepRetryTest#retryWritesNewAttemptRow`、`mvn -Dtest=SubRunTest#depthOverTwoRejected`、`mvn -Dtest=AutomationCeilingTest#stepLevelAboveRunCeilingRejected`。

### 四.5 REQ-WAG-15 异步任务与事件唤醒（P0，实现形态确认项）

`wait_task` 的提交与唤醒按 ECOS 实况落地：runtime-task 为**库形态**（`ITaskManagementService`，无 REST 层）⇒ Agent 侧经同 JVM 注入或 gateway facade 调用；任务完成事件经 **runtime-event + Kafka 既有 topic**（`ecos.agent`，基线 8 topic，**不新增 topic/容器**；若确需新增走 PMO 批次）唤醒 Run。事件信封对齐 REQ-PLT-22、DLQ 对齐 REQ-PLT-23。附件按"跨进程任务服务"的写法（异步 202 + task_id）保留对外语义、内部改实现。
**已批准（§十四 · R-45 推荐项 ①）**：runtime-task 保持库形态（同 JVM facade + runtime-event 唤醒），禁 Agent 自建调度。
**验收标识**：`mvn -Dtest=WaitTaskStepTest#taskSubmittedViaITaskManagementService`、`mvn -Dtest=RunWakeupTest#agentTopicEventResumesWaitingTaskRun`。

### 四.6 REQ-WAG-16 运行进度通道：SSE（依赖 P-4）∨ 轮询兜底（P1）

附件"进度用 SSE + Last-Event-ID 续传"在现状**不可达**：BFF `server.ts:85` 对上游响应 `await upstream.text()` 全缓冲且无 `text/event-stream` 分支 ⇒ 生产流式必挂（检视报告 WC-12）。本册要求：进度通道 = SSE（**前置件 P-4**：卷 06 F06-21 / 卷 08 BFF 直通）∨ P-4 未成前以轮询兜底并可观测降级标注；**不得**在 Agent 侧自建第二套流式通道（铁律 §2.5）。
**已批准（§十四 · R-44 推荐项 ①）**：生产 SSE 承流通道立为前置件 **P-4**，与卷 08 生产流式方案合并。
**验收标识**：`pw agent-run-progress.spec.ts > "进度事件在 P-4 开启下经 BFF 逐条到达（非整体缓冲）"`（P-4 未交付前记**未执行**）、`mvn -Dtest=RunProgressPollingTest#pollingFallbackUsedWhenSseUnavailable`。

---

## 五、工具、候选与治理（REQ-WAG-17~24）

### 五.1 REQ-WAG-17 Tool Contract 全要素与生命周期（P0）

工具必须登记契约才可被 Plan 选用：`name(<engine>.<capability>) / version(semver) / engine / category(read|compute|candidate|commit|notify) / side_effect(none|draft|production) / min_level / required_permission / input_schema / output_schema / timeout_ms / retry / idempotent / cost_class / evidence_output / data_classification / rollback_plan(commit 类必填) / endpoint / owner / sla / toolset / summary(≤60 字) / disclosure(always_loaded|searchable) / capability_deps`。生命周期 `draft→registered→validated→active→deprecated→retired`，仅 `active` 可用；破坏性变更发新主版本、旧版本保留至 Playbook 迁移完。**契约登记落 `ecos_ai`，路由前缀必须复用 ECOS 既有前缀的新增子路径并同步三滤波器**（禁引入 `cognition` 前缀；`/api/v1/agent` 与 `/agent-loop` 等并存受铁律 :435 Ant 路径陷阱约束）→ **R-43**。
**验收标识**：`mvn -Dtest=ToolContractRegistryTest#commitCategoryRequiresRollbackPlan`、`mvn -Dtest=ToolContractRegistryTest#onlyActiveVersionSelectable`、`mvn -Dtest=GatewayRouteIntegrityTest#agentToolRoutesResolveAndPrefixesRegistered`（须为真 HTTP 探测，不得沿用卷 07 C170 的源码 regex 冒充）。

### 五.2 REQ-WAG-18 渐进披露与权限过滤（P0）

可用工具 Schema 总量超上下文预算 30% 时自动启用三元工具 `tool_search(query, toolset?) / tool_describe(name) / tool_call(name,args)`；`always_loaded` 工具总数 **≤10**；**召回结果必须先按提交人权限、自动化等级、数据密级过滤**，无权工具不得进入 LLM 上下文；外部工具（含 MCP）必须包装为 Tool Contract 登记后方可用。附件 §5.4 的 `summary` 必填、能力依赖存在性、`always_loaded` 上限三项为契约测试硬项。
**验收标识**：`mvn -Dtest=ToolSearchTest#resultExcludesToolsUserLacksPermissionFor`、`mvn -Dtest=ToolContractValidationTest#alwaysLoadedCountBelowTen`、`mvn -Dtest=ToolContractValidationTest#capabilityDepsMustExistInCatalog`。

### 五.3 REQ-WAG-19 业务语义工具优先，禁暴露 CRUD（P1）

面向 Agent 的工具封装为业务语义（如"查项目利润""取项目验收状态""跑利润基线预测""找历史画像""建决策选项"），内部再调 Engine API；**不向 Agent 暴露通用 CRUD/SQL**（附件 D-07 + 卷 06 C138"允许 LLM 生成 SQL 直查"的既有缺陷不得复制到新工具集）。首批目录按附件 二册 §4.4 收敛后登记，每条必须可追溯到某 Engine 端点。
**验收标识**：`mvn -Dtest=AgentToolCatalogTest#noGenericCrudOrRawSqlToolExposed`、`mvn -Dtest=AgentToolCatalogTest#everyToolMapsToEngineEndpoint`。

### 五.4 REQ-WAG-20 Candidate 统一治理（P0）

Agent 的**唯一正发路径** = `Candidate → Review → Approve → Publish`。候选类型 12 种（数据映射/DQ 规则/实体/关系/指标/知识项/假设/情景/决策选项/行动草稿/认知模型定义/模型路由）；`payload` 按类型 Schema 校验并含 `payload_hash`、生成来源 Run/Step、置信度、依据（Evidence 引用）、五段校验（structure/references/conflict/impact/regression）、TTL 30 天。**收编既有重复载体**：`ecos_knowledge.kb_extract_candidate`（V140）与 `agent_approval`（V50）语义重复，统一控制面落 `ecos_ai`，KB 侧以适配/停写收编（存量待裁）→ **R-40**；`ecos_agent.*` 属主与是否部署 → **R-34/R-41**。
**验收标识**：`mvn -Dtest=CandidateServiceTest#lifecycleTransitionsAreLegalOnlyForwardChain`、`mvn -Dtest=CandidateServiceTest#expiredCandidateCannotBeApproved`、`mvn -Dtest=NoDuplicateCandidateStoreTest#kbExtractCandidateIsReadOnly`。

### 五.5 REQ-WAG-21 自动化等级 L0~L3、熔断与 Kill Switch（P0）

`L0 只读 / L1 建议 / L2 准备 / L3 提交`，**L3 只能由人或审批流触发，不可通过配置升为自动**；渐进上线 `shadow → assist → auto`；租户/系统级 **Kill Switch** 立即挂起执行中的 Run；风险评分超阈值时自动升级确认要求；工具熔断（窗口 1 分钟错误率 >50%、最少 20 次调用 ⇒ 熔断 30s 半开探测）与工具健康状态可见。
**验收标识**：`mvn -Dtest=AutomationLevelTest#l3NeverAutoTriggeredByConfig`、`mvn -Dtest=KillSwitchTest#activeRunsPausedImmediately`、`mvn -Dtest=ToolCircuitBreakerTest#opensAtFiftyPercentErrorRateAfterTwentyCalls`。

### 五.6 REQ-WAG-22 Policy Guard（PEP）五检查点与故障关闭（P0）

`pre-plan / pre-step / post-step / pre-commit / pre-output` 五检查点，裁决经 **security-engine**（`POST /api/v1/security/policy-engine/evaluate`，卷 06 R-18 的 REST 形态），**Agent 侧不得内嵌 PDP 或自实现策略判定**（铁律 §2.4-7）；决策效果 `allow / deny / require_approval / allow_with_mask`；security 不可用 ⇒ 默认 DENY（对 L1+ 拒绝）；post-step/post-output 执行脱敏、引用校验与工具调用白名单（**输出中的工具调用必须匹配当前 Plan**，附件 §5.5 提示注入防护）。AI 通道脱敏与 CLS **同权**，禁以聚合绕过（ST03-A ②）；小样本（分组 <5）保护作为 security-engine 策略项（Z-7）。
**验收标识**：`mvn -Dtest=PolicyGuardTest#fiveCheckpointsAllInvoked`、`mvn -Dtest=PolicyGuardTest#securityUnavailableDeniesForLevelOneAndAbove`、`mvn -Dtest=PromptInjectionGuardTest#toolCallOutsidePlanIsDropped`、`mvn -Dtest=MaskingParityTest#aiChannelMaskingEqualsUiChannel`。

### 五.7 REQ-WAG-23 发布链路：Commit 通道 × Git 归档 × 四眼 × 禁批量（P0）

L3 提交必须：① 携带有效审批凭证（四眼原则——生成人 ≠ 批准人）；② 定义/版本类资产（Agent 协同模型、认知模型、Playbook、Skill、Prompt、工具契约）发布**经 runtime-access 的 Git 唯一出口**（`publish = commit + tag`，DB 只存在用版本、历史进 Git，铁律 :549）——附件 §7.4"发布"未提 Git 归档，属必须补项（**R-40**）；③ **语义/口径/知识类候选禁批量通过**（逐条评审）；④ `commit` 类工具失败按 `rollback_plan` 回滚并审计。
**验收标识**：`mvn -Dtest=PublishServiceTest#publishWritesGitRefAndDbActiveVersionOnly`、`mvn -Dtest=ApprovalTest#generatorAndApproverMustDiffer`、`mvn -Dtest=BulkApproveGuardTest#semanticMetricKnowledgeCandidatesRejectBatchApprove`。

### 五.8 REQ-WAG-24 Evidence / Claim 绑定与数字一致性（P0）

每个关键 Claim 必须绑定 ≥1 Evidence 引用；**金额/比率/概率只准来自确定性计算（CM-01~05）或 Data Engine 事实，禁 LLM 生成**；叙述中的数字与引用必须经校验（`verify_claims`）通过才可发布，**校验失败后重生成最多 2 次**，仍失败即降级标注并阻断发布；关键 Claim 的证据覆盖率 = 100%；Evidence 链可回放至源对象与版本（向量与检索片段**不作为**证据来源，附件 §16.6）。
**已批准（§十四 · R-48 推荐项 ①）**：附件 40 AC + 35 AM 全量改写为可执行验收标识；P-1~P-5 未闭环前一律记"未执行"。
**验收标识**：`mvn -Dtest=ClaimEvidenceTest#everyCriticalClaimHasAtLeastOneEvidence`、`mvn -Dtest=NumericConsistencyTest#regenerationAttemptsCappedAtTwo`、`mvn -Dtest=NarrativeGuardTest#numberNotFromDeterministicSourceIsRejected`、`mvn -Dtest=EvidenceChainTest#vectorChunkIsNotAcceptedAsEvidence`。

---

## 六、跨域项挂靠（本册不重复立 REQ）

| 附件主题 | 挂靠 REQ | 归属 PRD/卷 | 裁决 |
|:--|:--|:--|:--|
| Model & Capability Center（13 类模型 / Capability 抽象 / Model Router 六因素 + DG-M1 回退） | REQ-AI-06~11 | PRD-06 / 卷 06（与 **F06-18 `ecos_ai` 空壳归位合并为同一批次**，禁两处定义） | **R-47** |
| Cognitive Model Registry（CM-01~07 定义/版本/评估 + 四类发布门禁 contract/regression/backtest/calibration） | REQ-COG-06~09 | PRD-05 / 卷 05（落位随规则更正：定义属 C 控制域、计算产物属业务域） | **R-42** |
| K = 12 类封闭枚举 + 写入拒绝钩子；C = 11 类对象补齐（situation / reasoning_trace / pattern_match / decision_basis） | **REQ-KB-06~09**（前缀定版，原写作 REQ-KNO 作废）、REQ-COG-06~09（两项已分别落 PRD-04 v1.1 §六 / PRD-05 v1.1 §六） | PRD-04 / PRD-05，禁在 Agent 控制面复制 | **R-46** |
| ECOS CLI（9 命令族、L3 需 `--approval-token`、禁直连库表、审计 `client=cli`、契约同源 D-11） | **REQ-PLT-27**（CLI 控制面）+ **REQ-PLT-26**（其契约基准） | PRD-01 / 卷 00，载体 = 仓根 `tools/`（Q4 `ecos-mcp-server.py` 先例，**不新增 Maven 模块**） | **R-37 / R-38**；**结案前置 = R-50（P-5 机器可读契约，当前全仓 0 个）** |
| 单一 Agent Runtime 与 Hermes/MCP 边界（Skill / Cron 迁 runtime-task） | REQ-AI-01~05 修正 + 卷 06 | PRD-06（附件 A-08 与代码事实相反：`HermesMCPClient` 已被 `CronJobController`/`SkillController` 注入） | **R-39（第一阻塞项）** |
| 前端 P01~P13 页面与 8 要素界面设计 | REQ-FE-10~13 | PRD-08 / 卷 08（复用既有路由族，不建平行入口树；界面验收走 P-3） | **R-49** |
| 多租户 `tenant_id`/`org_id` 补齐与 security RLS 贯通 | REQ-PLT-24~25 | PRD-01 / 卷 00~01 | **R-36** |
| **engine→services 依赖方向倒置**（`ai-engine-impl/pom.xml:83-88` compile 直依 `agent-service` 取 `HermesMCPClient`；ArchUnit 无此禁令） | **REQ-PLT-28**（v1.1 新立） | PRD-01 §3.2 / 卷 06（SPI `AgentRuntimeProvider` 落 `llm-gateway`，实现留在 agent-service，由 aiming 组装注入） | 门禁属 **M0 不等裁决**（ADR-19 §2.1）；宿主身份与双 Runtime 合并处置随 **R-39 / R-41** |
| **`agent-service` 随 `aiming`:18084 一起部署运行**（`services/aiming/pom.xml:15,18-25,41-45` 自证）——"写入方未部署"表述**作废** | 不新增 REQ（事实更正） | PRD-06 / 卷 06 + ADR-17 §2.4 / ADR-19 §1.2 | **R-41（v2.0 已按实测更正）**；文更正直改，处置待裁；运行期是否被 `search_path` 兜走属**待只读复核**，禁止据推断改代码 |
| **仓内零机器可读 API 契约**（`find docs -iname *.yaml -o -iname *.yml` 返回空）⇒ CLI/契约/E2E 三处验收无可校验基准 | **REQ-PLT-26** + 前置件 **P-5** | PRD-01 §3.2 / 卷 00 | **R-50**（= R-38 的结案前置）；缺口登记属 M0 直改 |
| **事件底座契约与 topic 单源**（册三 §11：十字段信封 + 36 发布名 + 7 订阅类 + 投递 5 规则；实测 `DomainEvent` 仅 **7** 字段且无 `tenant_id` ⇒ 租户分区键不成立、`KafkaTopics` 单源 **11** 常量 vs `kafka-init` 实建 **8**、附件 `ai.*`／`cognition.*` 共 **6** 个事件名无落点、`topicForAggregate()` 的 `action` 与在位 `ActionExecutedEvent` 同名不同义） | **REQ-PLT-29**（PRD-01 v1.3 新立，**v1.3 随 §十四 已批准**）；本册 **REQ-WAG-15**（异步任务与事件唤醒）为其消费方，不重复立 REQ | PRD-01 §3.2 / 卷 00 + 卷 10 §9.1 | **已批准（R-68 ②＋③过渡／R-69 ②／R-70 ②）**；`event_type` 取值表以卷 10 §9.1.2 为唯一 CHECK 源；执行边界（**R-70 ②**）：**以现有 8 个 `ecos.*` topic 承载 36 发布名中的 30 名**，剩余 6 名与 3 个缺建 topic 属**基建变更 ⇒ 需单独批准**，故本轮**不新建 topic、不改 `kafka-init`**；DDL **只落脚本文件、不实跑库**；信封按 **R-68 ②** 以子类 `WAgentEvent` 扩展 5 字段（**不动 `DomainEvent`**），过渡期"跨租户读写=0"**必须实测**而非文档承诺；发布一律经 runtime-event（铁律 §2.5） |
| **模型管理员／认知模型负责人两角色**（册一 §9.1 十角色；实测 `seed.sql:5-8` 仅 3 角色 `ADMIN`／`OPERATOR`／`VIEWER`，全仓对两角色及其命名变体 **0 命中**） | **REQ-SEC-11**（PRD-07 v1.2 §5.6 新立，**v1.3 随 §十四 已批准**） | PRD-07 §5.6 / 卷 06（模型中心）+ 卷 05（CM Registry）+ 卷 10 §9.5 | **已批准（随 R-72 ①）**，与卷 09 **R-66 ①** 并案（阈值/角色经 sysman 配置单源 + OPA 策略）；**批准的是"新增两系统角色"这一需求文本**，其余 8 角色与既有角色的映射**待只读复核**，复核前不合并、不改名、不动 `seed.sql`（存量只定性） |
| **测试体系 14 层 + 15 项门槛 + 3 类数据集 + LLM 变更四步**（册三 §15） | 不新增 REQ（口径由既有 **REQ-NF** 与本册 §七 追溯承接）；场景包**复用度量 4 项**写入卷 10 §8.3 门禁 | PRD-00 §REQ-NF / 卷 10 §9.6 与 §8.3 | **R-48**（验收口径）；§9.6 标"未建"的 **10** 个测试层级在 P-3／P-5 建成前**一律记"未执行"**；脱敏真实数据集须经批准且敏感列**逐列**过 ST03-A 豁免登记（控制域敏感列永不可豁免） |

---

## 七、追溯矩阵（REQ ↔ 附件锚点 ↔ ECOS 落位 ↔ 裁决）

| REQ | 附件锚点 | ECOS 落位 | 裁决/依赖 | 优先级 |
|:--|:--|:--|:--|:--:|
| WAG-01 | 一册 A-01；二册 §1.3 | 术语节（PRD-00 + ARCH_SPEC） | R-31 | P0 |
| WAG-02 | 三册 §10 边界表 | ai-engine（W Owner）/ cognitive `ecos_decision*` 定性 | R-32 | P0 |
| WAG-03 | 二册 §10.3/§11 | ST09 + 铁律 §2.5 + §0.6 | 同向（无需裁决） | P0 |
| WAG-04 | 三册 §1.1~§1.2/§12.3 | `ecos_ai` + security-engine RLS/CLS + Git | R-33/34/35/36/41 | P0 |
| WAG-05 | 一册 §1/§3；三册 §2.1~2.2 | `ecos_ai` 三表 + 应用层校验 | R-35 | P0 |
| WAG-06 | 一册 §3；三册 §2.1 `slots_source` | `ecos_ai` + 前端 Goal 卡 | R-49 | P1 |
| WAG-07 | 三册 §2.2/§3.2/§4.1 | `supersedes_*` / `previous_run_id` / `pinned` | R-35 | P0 |
| WAG-08 | 一册 §2.4；三册 §3.1~3.2 | Readiness 服务 + 配置单源 + Engine 只读探测 | R-36 | P0 |
| WAG-09 | 三册 §3.1 `agt_readiness_gap` | `ecos_ai` + 子 Run 委派 | — | P1 |
| WAG-10 | 一册 §2.5；二册 §14.3 | 降级枚举入配置单源 + Run 标注 | R-46 | P0 |
| WAG-11 | 二册 §2.2/§2.5/§2.6 | Run Store（`ecos_ai`）+ runtime-event | R-39 | P0 |
| WAG-12 | 二册 §3.1~3.3 | Plan DAG + Playbook（Git 归档） | R-40 | P0 |
| WAG-13 | 二册 §3.2/§4.3 | 探索 Plan + Human Gate | — | P0 |
| WAG-14 | 二册 §3.4；三册 §4.1~4.2 | Step 表 + 引用/哈希 + 子 Run | R-35 | P0 |
| WAG-15 | 二册 §11.3；三册 §10.1 | `ITaskManagementService` + `ecos.agent` topic | **R-45** | P0 |
| WAG-16 | 三册 §10.1；一册 §11.1 NFR | BFF SSE 直通（**P-4**）∨ 轮询 | **R-44** | P1 |
| WAG-17 | 三册 §5.1~5.4；二册 §4.1~4.2 | 契约表 + 三滤波器 + 前缀复用 | **R-43** | P0 |
| WAG-18 | 二册 §4.3；三册 §5.4 | `tool_search/describe/call` + 权限过滤 | R-43 | P0 |
| WAG-19 | 二册 §4.4 业务语义工具 | Engine 端点封装 | R-32 | P1 |
| WAG-20 | 三册 §6.1~6.3 | Candidate 统一落 `ecos_ai` | **R-40/34/41** | P0 |
| WAG-21 | 一册 §8；二册 §13；三册 §13 | 等级 + 熔断 + Kill Switch | — | P0 |
| WAG-22 | 二册 §12；三册 §11/§12.4 | security-engine `evaluate` + obligations | R-35/42 | P0 |
| WAG-23 | 三册 §7.4/§6.3；二册 §13.3 | runtime-access GitService + 四眼 + 禁批量 | **R-40** | P0 |
| WAG-24 | 一册 §6.5；二册 §5.4/§9；三册 §7/§15.2 | Claim-Evidence + 数字一致性 | **R-48** | P0 |

---

## 八、非功能与验收门禁

| 项 | 要求（附件 → ECOS 口径） |
|:--|:--|
| SLO | 附件：Run 成功率 ≥99.5%、Goal 卡 P95 ≤3s、Readiness P95 ≤30s、预测 Run P95 ≤10min、数字一致一次通过 ≥98% → 全部改为**可执行标识 + 阈值断言**（`mvn -Dtest=SloThresholdTest#...` / `pw` 性能用例），未建载体前记**未执行**（R-48） |
| 事件 | Outbox 至少一次 + 分区有序 + DLQ，对齐 REQ-PLT-22/23，复用 `ecos.*` 8 topic 基线（R-45） |
| 审计 | 哈希链 + WORM；**审计一律走 Kafka `ecos.audit`（ST06）**，附件 §12 的 `agt_audit_event` 自建哈希链须改为经 security/审计底座，禁 Agent 侧自实现（ST06 + 铁律 §2.4-7） |
| 保留与分区 | 附件"Run/Step 3 年、事件按月分区、1 年转冷"→ ECOS 侧改由 runtime-monitor/冷存策略承担（禁 PG 原生分区语法，MC01~MC06 多库兼容），只加不改 |
| 门禁 | 前置件 **P-1**（AI 测试底座）、**P-2**（workspace 两态底座）、**P-3**（Playwright 工程，任务 #30）、**P-4**（SSE 经 BFF，新增登记）；P 系列未闭环 ⇒ 相关标识一律"未执行" |
| 状态 | 本册需求文本**已批准**（2026-09-29 用户批量批准，凭证 = [需求检视报告 §十四](需求检视报告-2026-09-28.md)，依 **Q12** 不另补分册 APPROVAL_RECORD）：**R-31~R-50**（20 项，含 v2.0 更正的 R-41 与新立的 R-50）+ 承接的 **R-68~R-72** 全部按各表「本设计推荐」列定版；ARCH_SPEC **Gate-1 已签字 / Gate-2 已通过**（`deliverable_allowed = true`，凭证同上）。**批准 ≠ 已实现**：本册所涉 `ecos_ai` 新表、`/api/v1/wagent/*` 路由、Agent Runtime 合并等**实测落地 0**；DDL 按批准口径**只落迁移脚本文件、不实跑库**；改业务 Java/TS 代码、新建 topic／改 `kafka-init`、实跑迁移、git 提交推送**需逐项再授权**（报告 §14.4）。临界项 **R-31/32/33/35/37/39/42/48** 已批准，但 **R-50（P-5 契约单源）是 R-38 的结案前置**——P-5 未闭环前 CLI 不得开工 |

---

## 九、附件工期与人力的处置（不另开时间线）

附件三册 §17 的"13 人 / 39 周（G0 3 周 + P0~P4 36 周）"**不并入 ECOS 工程计划**（v1.3 更正口径：现网 Q1~Q14 与 R-1~R-72 **已全量批准**（凭证 = 需求检视报告 §十四）、ARCH_SPEC Gate-2 已通过，但**前置件 P-1~P-5 实测未闭环**——P-3 Playwright 工程未建成、P-5 机器可读契约全仓 0 个、P-4 生产 SSE 承流未立项 ⇒ 附件工期仍**不并入**，只按批次挂靠）。改为**批次挂靠**（R-49 ①：本轮登记不排期）：

| 附件阶段 | 挂靠 ECOS 批次 |
|:--|:--|
| G0 边界冻结 | 规则更正批次（R-33 schema 落位 / R-35 DDL 形态 / R-42 铁律 §0.6 措辞） |
| P0 控制面 + 运行 | 卷 06（智能域）批次 + 本册 F10 系列 |
| P1 编排与工具 | 卷 02 / 03 / 04 批次 |
| P2 决策与行动 | 卷 05 / 07 批次 |
| P3 场景竖切片 | 卷 07 / 09 批次 |
| P4 学习与验收 | 卷 09（年度经营预测 FC-01~07）+ AC 映射表 |

> 命名与编号接续：本册功能编号 **F10-01~F10-24**（详细设计-10 使用）、改造项 **W245~W274**、ARCH_SPEC §十一 回填 **C227~C256**、DDL 起号 **V227**（实测单源目录最大 V163；卷 08 预留 V214、卷 09 预留 V215~V224；交付前须再实测定稿）。
