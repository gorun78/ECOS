# PRD-05 cognitive-engine 需求规格（分册 05）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 契约上游: `docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_SERVICE_DOC.md` + `BUSINESS_SCENARIO_COGNITION_DOC.md`（在途 PMO-66，本册为其需求侧细化，不替代其契约地位）
> 覆盖: REQ-COG-01~05
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
- 数据源：`kb_mind_registry` JOIN 场景 Mind 绑定（绑定表名以 Pg 实查为准，PRD-01 DB-03 预检）。

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

## 六、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| COG-01 | kb 三表迁移到位（DB-03 预检）、Mind 绑定表实查 | PRD-08 WS-03 前端、KB-04 守护测试 | PMO-66（在途） |
| COG-02 | PRD-09 FC-02 运行快照只读端点 | FC-04 风险解释、演练 4 | 场景批次 B（M2） |
| COG-03 | FC-02 情景运行 | 演练 4 | 场景批次 B（M2） |
| COG-04 | COG-01 | — | PMO-66 同批 |
| COG-05 | ARCH-07 | 全部 cognitive 变更 | 持续守护 |

<!-- PRD-05-cognitive-engine需求规格 / 2026-09-28 / v1.0 -->
