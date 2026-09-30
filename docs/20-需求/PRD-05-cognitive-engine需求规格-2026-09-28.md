# PRD-05 cognitive-engine 需求规格（分册 05）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.2（**v1.2（2026-09-29 批量批准定版）**：需求检视报告 **§十四** 按各表「本设计推荐」列批准 R-1~R-72 ⇒ 本册 §六 **REQ-COG-06~09 转正式需求并计入已批准基线**（随 **R-42** 与 **R-14 组**）。**"已批准"仅指需求文本生效**：验收测试类未建者一律记"未执行"；`ecos_cognitive` 落位与 R-13/R-14 相关的**存量归位只定性不擅迁**，DDL 只落**迁移脚本文件**、实跑库需逐项再授权（报告 §14.4）。v1.1（2026-09-29 接续 W-Agent 制品，时点标注"草案、待裁决"已被 §十四 取代）：新增 §六 REQ-COG-06~09 承接需求；§1.1 E1 数据源表名按已结案裁决 **Q11 / ADR-8** 更正，禁用 `kb_mind_registry` 误名）
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 契约上游: `docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_SERVICE_DOC.md` + `BUSINESS_SCENARIO_COGNITION_DOC.md`（在途 PMO-66，本册为其需求侧细化，不替代其契约地位）
> 覆盖: REQ-COG-01~05（已列）+ **REQ-COG-06~09（v1.1 立，2026-09-29 §十四 **已批准**）**
> 模块: `ecos_backend/engine/cognitive-engine`（run in aiming:18084）

---

## 一、REQ-COG-01 E1–E4 场景认知端点（P0）

### 1.1 端点契约细化

> 路径与错误语义冻结自 BUSINESS_SCENARIO_SERVICE_DOC §3.1；本节补齐开发可直接实现的 Schema 与错误矩阵。

**E1 `GET /api/v1/business/scenarios/{id}/cognition/detect`**

```json
// 200
{"code":0,"message":"ok","timestamp":1790495600000,"data":[
  {"mindId":"M001","name":"经营诊断Mind","capabilityMask":["DETECT","FORECAST"],
   "closedLoopBounds":["PRE","POST"],"isActive":true}
]}
```
- 场景无 Mind → `data: []`（200，不是 404）；
- Mind 行存在但 `is_active=0` → 不返回该 Mind；
- 数据源（v1.1 更正，按 **Q11 / ADR-8 §5**）：Mind 注册表真身**不存在**——全库无名字含 `mind` 的表、`mind_id` 列 0 个（ARCH_SPEC **C98** / 详细设计-05 **X-19**）；`kb_mind_registry` 与 `kb_cognitive_*` 均为 PRD 误名，**禁止在任何新文档中继续使用**。本项契约以详细设计-05 §Cg-2 新建的 `cognitive_mind` + `cognitive_scenario_mind` 为目标载体（表未建 → E1 在 QA 前必须 `to_regclass` 预检并判"未执行"，禁写 200 空数组蒙过）。

**E2 `GET .../cognition/operation-eval?mind={mindId}`**

```json
{"code":0,"data":{"mindId":"M001","metrics":[
  {"metricId":"op-1","name":"验收治理","capability":"DETECT","score":0.83,"trend":"FLAT"}
]}}
```
- 计算：`score(belief_i) = belief.probability × trend_weight(belief.domain, scenario)`；**trend_weight 本期固定 1**，预留配置 `ecos.cognition.eval.trend_weight`（Properties 入口，不实现动态）；
- 排序：score desc，Top-N（默认 N=10，`?limit=` 可调，上限 50）；
- trend 枚举：`UP/FLAT/DOWN`（本期无外部时序预测，全部 FLAT——响应必须真实标注，禁伪造趋势）。

**E3 `GET .../cognition/hypotheses?mind={mindId}`**

```json
{"code":0,"data":[
  {"hypothesisId":"H003","reason":"毛利被采购成本侵蚀","confidence":0.82,
   "belief":"BELIEVED","status":"EVIDENCED","mindId":"M001","updatedAt":"..."}
]}
```
- 过滤语义：无 mind 参数 → 场景全部假设；带 mind → `AND mind_id=?` 严格过滤；
- status 冻结枚举：`PROPOSED/EVIDENCED/BELIEVED/REFUTED`；confidence ∈ [0,1]，前端三色分段 `[0,0.5)低 / [0.5,0.8)中 / [0.8,1]高`；
- 查询条件恒含 `is_deleted=0`。

**E4 `GET .../cognition/beliefs?mind={mindId}`**

```json
{"code":0,"data":[
  {"beliefId":"B001","capability":"REVIEW","domain":"采购","probability":0.91,
   "strength":0.7,"updatedAt":"2026-09-27T08:30:00Z"}
]}
```
- `capability = mind.capability_mask ∩ belief.domain 映射`（本期字符串直等匹配，见 §四 COG-04）；交集为空 → capability=null（前端显示"未映射"，不丢弃行）；
- 排序：updatedAt desc。

### 1.2 错误矩阵（四端点通用）

| 条件 | HTTP | body code | message |
|---|:--:|:--:|---|
| 场景不存在 | 404 | 404 | scenario not found |
| 场景已软删除 | 404 | 404 | scenario deleted |
| `mind` 参数不属于该场景 | 400 | 400 | mind not found on this scenario（**禁静默兜底返回全部**） |
| `mind` 指向不存在的 Mind（E1 detect 带参时） | 400 | 400 | mind not found |
| cognitive 引擎未启用/stub 无 LLM | 503 | 503 | cognitive engine not enabled（**原样穿透，禁 200 空 body**，用户裁定 A3） |
| kb 三表缺失（迁移未跑） | 503 | 503 | cognitive schema not ready（区别于 not enabled，便于运维定位） |

### 1.3 横切要求

1. traceId：从 `X-Request-Id` 读并写入 MDC；日志格式 `EVT=CognitiveAxB op={detect|eval|hyp|belief} agent={} scenario={} mind={} cost={}ms`；
2. 只读：四端点仅 SELECT kb 三表 + mind registry，无任何写操作；
3. 鉴权：业务数据端点**不写 permitAll**（默认 DENY），过三滤波器；
4. 前端继承 apiFetch，不改调用方。

### 1.4 验收标准

1. 四端点 × {正常/空场景/无 Mind/场景删除/mind 不属/503} 全组合集成用例（`ScenarioCognitionIntegrationTest`）；
2. 503 穿透验证：stop cognitive stub → 前端右栏置灰 banner（PRD-08 WS-03）；
3. `to_regclass` 预检脚本先行（PRD-01 DB-03）。

---

## 二、REQ-COG-02 领域化因果诊断（P1）

### 2.1 需求陈述

`CausalReasonerService` 诊断入口扩展**预测运行上下文**，输出经营因果路径，证据与假设分离显示。不改变既有通用诊断契约（只增字段，只增不改）。

### 2.2 契约扩展

`POST /api/v1/cognitive/diagnose`（既有端点，body 增可选块）：

```json
{
  "domain": "biz-profit",
  "deviation": {"metricCode": "M_FC_PROFIT", "expected": 1200000, "actual": 980000, "period": "2027-08"},
  "forecastContext": {                          // 新增可选块
    "forecastRunId": "fr-001", "caliberId": "cal-01", "caliberVersion": "3",
    "asOfTime": "2027-08-31T00:00:00Z",
    "projectId": "p1", "departmentId": "d01"
  }
}
```

**响应因果路径结构（新增字段）**：

```json
{"code":0,"data":{"chains":[
  {"chainId":"c1",
   "path":[{"node":"进度延期","type":"EVENT"},{"node":"验收率下降","type":"METRIC:M_REALIZATION_RATE"},
           {"node":"验收实现额下降","type":"METRIC:M_ACCEPTANCE_AMT"},{"node":"利润下降","type":"METRIC:M_FC_PROFIT"}],
   "contribution": -180000,
   "evidenceRefs":["ev-101","kg-edge-233"],       // 有证据支撑的边
   "statSupport": {"method":"effect-estimate","pValue":0.03},
   "unverifiedHypotheses":[{"statement":"外包比例上升放大延期影响","reason":"无反向证据累计"}]}
]}}
```

### 2.3 规则

1. **证据与假设分离**：路径边有 evidenceRefs/statSupport → 显示为"证据支撑"；仅有假设 → 归入 `unverifiedHypotheses`，UI 标注"待验证假设"（联动 PRD-08 右栏与 PRD-09 演练 4）；
2. `forecastContext` 存在时，诊断取数范围锁定该运行快照（调预测运行服务只读端点，PRD-09 FC-02），不得另行全库扫描；
3. contribution（因素贡献额）必须可由快照数据复算（确定性计算，禁 LLM 生成数值——铁律 §0.2.1）；
4. 无 forecastContext → 维持既有通用诊断行为（向后兼容）。

### 2.4 验收标准

1. 验收延期情景诊断返回链 `进度延期→验收率↓→验收额↓→利润↓`，contribution 与情景 diff（COG-03）一致；
2. 无证据陈述全部落 unverifiedHypotheses，chains.path 中无未证实边；
3. 不带 forecastContext 的旧请求响应 schema 不变（契约回归）。

---

## 三、REQ-COG-03 反事实由预测运行驱动（P0）

### 3.1 需求陈述

情景模拟（counterfactual）在经营预测语境下必须是**确定性重算**而非 RAG+Agent 推演：以基准运行快照为底，仅覆盖指定范围输入，重跑确定性计算，逐项比较。

### 3.2 契约

`POST /api/v1/cognitive/simulate`（既有端点）新增模式，或场景侧经 workspace 编排调预测运行服务——**裁定：反事实计算主体落预测运行服务（PRD-09 FC-02 的 scenario 运行），cognitive 负责因素贡献归因与解释**。分工：

```
workspace 情景复制 → 预测运行服务：baselineRunId + overrides → 新 forecastRunId（scenario 类型）确定性重算
                  → cognitive：对 (baselineRun, scenarioRun) diff 做归因（COG-02 链）与解释
```

**overrides 结构**：

```json
{"baselineRunId":"fr-001",
 "overrides":[{"projectId":"p1","departmentId":null,"periods":["2027-07","2027-08","2027-09"],
               "target":"stage.realization_rate","stage":"ACCEPTANCE","delta":-0.10}]}
```

### 3.3 规则（禁令）

1. 新情景运行**只覆盖 overrides 命中的项目/部门/期间输入**，其余全部继承基准快照（逐项 diff 仅限目标范围）；
2. `ScenarioSimulatorServiceImpl`（RAG 基线 + Agent 推演）**禁止**作为本链路金额来源——保留为探索式 What-if 交互，其输出必须带 `exploratory=true` 标记且 UI 显示"非核算口径"；
3. 情景运行同样满足六要素（PRD-09 FC-03）：独立 forecastRunId + 快照 + 口径版本；
4. 归因输出 contribution 合计 = 情景与基准利润差（±精度容差 0.01），不平即 FAIL。

### 3.4 验收标准

1. "验收延期"情景：仅 p1 项目 7–9 月输入变化，其他项目/月份 diff=0；
2. contribution 汇总平衡校验通过；
3. exploratory 通道输出带标记且不出现在核算报表。

---

## 四、REQ-COG-04 capabilityMask 枚举与映射（P1）

### 4.1 枚举落点

`com.chinacreator.gzcm.engine.cognitive.api.mind.MindCapabilityMask`（cognitive-engine-api 包内，唯一权威）：

| 值 | 语义 | 算子来源 |
|---|---|---|
| DETECT | 诊断类算子 | DiagnosisController |
| FORECAST | 预测算子 | ForecastController |
| SIMULATE | 反事实 | CausalReasonerService#simulate |
| PLAN | 政策/规划 | CognitivePlannerController |
| REVIEW | 事后评语 | 预留常量 |

### 4.2 belief.domain ↔ capabilityMask 映射

1. 本期规则=字符串直等（domain 值与枚举名或其中文别名表匹配）；匹配不到 → capability=null + issue 记录（不阻断）；
2. 正式版预留：KB 维度 recode（domain 归一到能力维度），**不在本批实现**，疑义已登记于 BUSINESS_SCENARIO_COGNITION_DOC §4.1；
3. 别名表落配置（`ecos.cognition.domain_alias`），新增别名不改代码。

### 4.3 验收

1. 枚举报表（API 包内枚举与契约文档 §2.2 表一致）；
2. E4 响应 capability 按直等规则正确投影；未匹配行 capability=null 且不丢弃。

---

## 五、REQ-COG-05 铁律守护（P0，守护型）

| 守护项 | 机制 |
|---|---|
| 推理结果不落盘 | 集成断言（PRD-01 §2.3-2）：diagnose/forecast/simulate 调用前后 DB 行数不变 |
| 不新增业务事实表 | ARCH-07（PRD-01 §1.4）+ DDL 评审：cognitive 域仅 4 表 |
| 不引入规则引擎 | 依赖审查：cognitive pom 无 drools/easy-rules 等；SpEL 即可 |
| 禁止 LLM 直接承担推理职责 | 评审红线：LLM 输出仅可作解释文本/假设候选，凡进入 chains/contribution/score 的数值必须确定性可复算；llm 调用走 llm-gateway |
| Context 不建表 | 运行时上下文（forecastContext 等）仅内存/请求态，禁 cognitive_context 表 |

**验收**：ArchUnit + 评审 checklist + 上述集成断言全绿。

---

## 六、W-Agent 承接需求（REQ-COG-06~09，v1.1 立，**2026-09-29 §十四 已批准**）

> **来源**：附件第二册 §16.2（对象归属总表）/§17.x（Cognitive Model 契约与发布门禁）、第三册 §A.3、第一册 §5~§6；登记动因见 [W-Agent 落地检视报告 §七 逐字计划](./W-Agent详细设计落地检视报告-2026-09-29.md) 与 [PRD-10 §六 挂靠表](./PRD-10-W%20Agent赋能DIK-C需求规格-2026-09-29.md)。
> **裁决状态**：本四项**已批准**（2026-09-29 需求检视报告 §十四 14.1，随 **R-42 ①** 规则文字更正与 **R-14 ①** 契约属主），落位与表名以本册"真身"列为基准；**执行边界**：新表 DDL 按批准口径落**迁移脚本文件**（不实跑库），`ecos_cognitive` 归位按 **R-13 ①** 先做 `{schema}.` 限定新写、**存量只定性不擅迁**；对外路径按 **R-14 ①** 冻结不变、内部 Service 以 subject 参数化，改业务 Java 代码需逐项再授权（报告 §14.4）。
> **边界**：C 层对象的**定义与落盘主权属 cognitive-engine**，W Agent 只消费、只引用版本，不得在 `ecos_ai.ecos_wagent_*` 复制 C 对象（ADR-16/17）；本节与 §五 REQ-COG-05 守护项同时生效，冲突时以 §五（铁律）为准。

### 6.1 REQ-COG-06 C 类 11 对象补齐与命名对齐（P0，守护 + 补齐型，随 R-42）

附件第二册 :1007 冻结 C 层 11 类对象。逐项与 ECOS **实测真身**对齐（只加不改，禁按附件名新建重复表）：

| 附件对象（C 11 类） | ECOS 实测真身（`information_schema` 只读查得） | 处置（推荐项） |
|---|---|---|
| evidence / hypothesis / belief | `public.ecos_cognitive_evidence`(`V127`) / `_hypothesis`(`V128`) / `_belief`(`V129`) | ✅ 已有，**正名以此为准**；`kb_cognitive_*` 误名按 Q11/ADR-8 §5 永久禁用 |
| cognitive_model_instance | `public.ecos_cognitive_model`(`V124`) 是模型**资产**（ADR-8 口径），**不是运行实例** | ⚠️ 资产与实例不得共用一表：实例属运行态，是否落盘随 R-42 裁；未裁前只在响应内以 `modelId + version + runId` 引用表达 |
| context/situation | 无载体（全库无 `situation` 表） | §五 REQ-COG-05 已判"Context 不建表"→ 情境属**请求态**；若 R-42 要求可回放快照，落 §6.4 业务域快照而非 cognitive 表 |
| reasoning_trace | 无载体 | 随 R-42：推荐**不落 cognitive 表**，以 `run_id` 关联审计流（ST06 Kafka `ecos.audit`）承载；附件 `cog_` 前缀 DDL 形态不采纳（MC01/DR01/ST07） |
| prediction | 无 cognitive 侧独立表 | 确定性预测**产物**按 **ADR-14** 落业务域 `ecos_dw`（见 §6.4），cognitive 侧只保留引用 |
| scenario | 双源并存：`public.ecos_world_scenarios` 与 `ecos_cognitive.ecos_wm_scenario` | ⚠️ 双载体先定性后处置（存量只定性不擅自迁移）；`ecos_cognitive.*` 内的 `ecos_wm_*`/`ecos_biz_*`/`ecos_goal_tracking` 属"业务域数据落引擎控制 schema"的 ST07 疑点，登记不改，随 R-42 一并裁 |
| causal_result | 邻近物为 `public.ecos_decision_causal_link`，语义不等价 | 不冒充；如需因果结果载体随 R-42 单独立项 |
| pattern_match | 无载体 | 同 reasoning_trace 处置路径 |
| decision_basis | `public.ecos_decision` / `ecos_decision_approval` / `ecos_decision_policy` / `ecos_decision_precedent` / `ecos_decision_exception` / `ecos_decision_causal_link`，另 `ecos_decision_case` 在 `public` 与 `ecos_ai` **双侧同名并存** | 归 C 层（见 §6.3）；`ecos_decision_case` 镜像对须先定性（谁是写权威），禁在两处同时开写口 |

**规格**：
1. 11 类对象必须有一张**对象目录**（代码内枚举 + 契约文档表，单源），每类映射到"唯一物理载体"或"引用态（无表）"，二选一，不得两态并存；
2. 新文档/新代码出现 `kb_cognitive_*`、`kb_mind_registry`、`cog_`/`agt_`/`aim_` 裸前缀建表 → 评审红线（Q11 / ADR-8 §5 / 数据库访问规范 DR01+ST07）；
3. cognitive 域表数量白名单（ARCH-07）随本项扩表时必须同批改，并补 `ecos_cognitive_run_invalidation`（否则 CI 误拦，见需求检视报告 Q11 结案口径）；
4. 附件 §16.6"对象类型白名单"与 PRD-04 REQ-KB-06 的 K 12 类白名单**互斥且同源校验**：同一类型名不得同时出现在 K 与 C 白名单。

**验收**（可执行标识）：
- `mvn -Dtest=CognitiveObjectCatalogTest#everyCClassHasExactlyOneCarrierOrNone`（11 类逐项断言）；
- `mvn -Dtest=CognitiveObjectCatalogTest#bannedMisnomersAbsentFromCodeAndDocs`（regex 扫 `kb_cognitive_`、`kb_mind_registry`、`cog_[a-z_]+ (`）；
- `mvn -Dtest=KCBoundaryWhitelistTest#cAndKTypeSetsAreDisjoint`（与 PRD-04 REQ-KB-06 同源对拍）；
- 界面侧（场景工作台卡片）→ `pw wagent-cognitive-cards.spec.ts`：**P-3 Playwright 工程未建成前一律记"未执行"**（铁律 v2.0:14）。

### 6.2 REQ-COG-07 Cognitive Model Registry：CM-01~07 × 版本不可变 × 四类发布门禁（P0，随 R-42）

**规格**：
1. 模型类型枚举冻结为 **CM-01 规则推理 / CM-02 统计推断 / CM-03 预测 / CM-04 因果 / CM-05 情景·反事实 / CM-06 案例推理 / CM-07 LLM 假设·语义推理**（附件第二册 :1056-1062）；新增枚举先改契约再入码；
2. **CM-07 的数值禁令**（与 §五"禁止 LLM 直接承担推理职责"同源，此处升级为可测断言）：金额、比率、概率**只准**来自 CM-01~05 或 data-engine 事实；CM-07 只能产出 hypothesis，且必须附 `evidence_refs / confidence / model_ref / source`，**不得**写入 `ecos_cognitive_belief.probability` 或任何预测结果列；
3. **版本不可变**：`ecos_cognitive_model` 已发布版本（`version_no` DR07 形态）行内容与哈希入 immutable 态后禁止 UPDATE；变更只能新建版本行并 `supersedes` 指向旧版；
4. **四类发布门禁**（附件第三册 §15.1 引第二册 §17.7）：`contract` / `regression` / `backtest` / `calibration` 全通过才可 `PUBLISHED`；任一缺项 → 状态停留 `DRAFT` 且发布端点返回带错误码的 4xx（不是静默成功）；
5. 注册表落控制域 cognitive 侧（`ecos_cognitive` 族），**不建** `ecos_ai.ecos_wagent_cognitive_model` 副本（ADR-17）；Agent 侧只经 REST 读（ST09）。

**验收**：
- `mvn -Dtest=CognitiveModelRegistryTest#publishedVersionRowIsImmutable`（发布后 UPDATE 必败）；
- `mvn -Dtest=CognitiveModelRegistryTest#publishRequiresAllFourGates`（四门禁 4×缺项组合参数化）；
- `mvn -Dtest=Cm07NumericGuardTest#llmTypeCannotWriteProbabilityOrAmount`（CM-07 写 probability/amount → 拒绝并返错误码）；
- `mvn -Dtest=CognitiveModelRegistryTest#modelTypeRejectsUnknownEnum`。

### 6.3 REQ-COG-08 Decision Basis 归属与镜像定性（P1，随 R-42）

**规格**：
1. `ecos_decision*` 表族（V103/V151 起，实测 7 张 + `ecos_agent` 域 1 张）**定性为 C 层 Decision Basis**（附件第二册 §16.2 :983），属认知域资产，**不改表、不改既有 API**（只增不改）；
2. **写权威单源**：`ecos_decision_case` 当前 `public` 与 `ecos_ai` 同名并存 ⇒ 必须先实测两侧行数与读写调用点判定权威，另一侧只停写不 DROP（存量只定性）；判据与处置登记入 ARCH_SPEC 追溯表，实测前本项记"待复核"，**禁止据推断改代码**；
3. Decision Basis 对 W Agent **只读**：Agent 侧引用决策依据只准带 `decisionId + version + hash`，不得回写、不得经 kb/ai 侧代理写（PRD-04 REQ-KB-04 同向）；
4. 快照/证据引用列若为 JSON 形态，必须 `TEXT` 且不参与 WHERE/JOIN/索引（**MC02**）。

**验收**：`mvn -Dtest=DecisionBasisOwnershipTest#decisionCaseHasSingleWritableCarrier` + `#agentPathCannotWriteDecisionBasis`（只读端点集合断言，无写路由）。

### 6.4 REQ-COG-09 确定性计算产物落 `ecos_dw`（P0，随 ADR-14，R-14 组已结案口径的承接）

**规格**（本项**不是新裁决**，而是把 ADR-14 的 Accepted 决策承接为 cognitive 侧可测义务）：
1. 计算主体 = cognitive-engine 的确定性组件（`DeterministicForecastCalculator` 暂名），与 `ScenarioSimulatorServiceImpl`（What-if 探索）、`CausalReasonerService`（事后解释）**三者职责互斥、不得互相回退**；
2. 产物（run / result / result_detail / backtest_metric）**落业务域 `ecos_dw`（APPLICATION 层）**；DDL 由 data-engine 持有、迁移入单源目录；cognitive **经 data-engine 写通道 REST 提交**，禁止直连 `ecos_dw`、禁止在 `ecos_cognitive`/`ecos_knowledge` 建金额结果表（ST08）；
3. 幂等六要素：同 `forecastRunId + caliberId@version + asOfTime + 输入快照 + 公式版本 + 证据引用` 重跑必须同明细；输入快照 JSON 列 `TEXT`、不建索引（MC02）；
4. **LLM 不参与数值**：区间来自 PRD-04 REQ-KB-08 已发布画像版本；无来源数值判 FAIL；
5. `caliberId`/公式主权 = ontology（ADR-14 §2.4），cognitive 只引用版本不复制定义。

**验收**：
- `mvn -Dtest=DeterministicForecastArtifactTest#resultsLandInEcosDwNotEngineSchema`（DDL 只读形态断言，库侧仅 SELECT/catalog）；
- `mvn -Dtest=DeterministicForecastArtifactTest#rerunWithSameSixFactorsIsByteIdentical`；
- `mvn -Dtest=DeterministicForecastArtifactTest#snapshotJsonColumnsAreTextAndUnindexed`（MC02）；
- `mvn -Dtest=ModuleDependencyArchTest#cognitiveDoesNotWriteEcosDwDirectly`（禁止绕 data-engine 写通道）。

---

## 七、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| COG-01 | kb 三表迁移到位（DB-03 预检）、Mind 绑定表实查 | PRD-08 WS-03 前端、KB-04 守护测试 | PMO-66（在途） |
| COG-02 | PRD-09 FC-02 运行快照只读端点 | FC-04 风险解释、演练 4 | 场景批次 B（M2） |
| COG-03 | FC-02 情景运行 | 演练 4 | 场景批次 B（M2） |
| COG-04 | COG-01 | — | PMO-66 同批 |
| COG-05 | ARCH-07 | 全部 cognitive 变更 | 持续守护 |
| COG-06 | Q11/ADR-8 正名（已结案）、PRD-04 KB-06 同源白名单 | PRD-10 WAG-17、COG-07 | **已批准（随 R-42 · §十四）** |
| COG-07 | COG-06 目录、附件第二册 §17.7 门禁定义 | PRD-10 WAG-05/14、PRD-07 SEC-07 | **已批准（随 R-42 · §十四）** |
| COG-08 | COG-06、`ecos_decision_case` 双侧实测定权威 | PRD-10 WAG-19/20（回流引用） | **已批准（随 R-42 · §十四）** |
| COG-09 | ADR-14（Accepted）、PRD-04 KB-08 画像版本、PRD-02 DATA 写通道 | PRD-09 FC-02/03、PRD-05 COG-02/03 | 场景批次 A（**不待裁决**，仅随本册登记） |

<!-- PRD-05-cognitive-engine需求规格 / 2026-09-29 / v1.2（§六 REQ-COG-06~09 已批准 R-42/R-14 组，凭证 = 需求检视报告 §十四 2026-09-29） -->

<!-- PRD-05-cognitive-engine需求规格 / 2026-09-28 / v1.0 -->
