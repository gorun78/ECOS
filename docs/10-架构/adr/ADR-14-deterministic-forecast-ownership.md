# ADR-14 — 领域确定性计算的归属：cognitive 主责 + 业务域落库 + 场景只编排

> 来源: 需求检视报告 §十二 Q3（用户选择"cognitive/dccheng 之一"，本 ADR 为二者中的裁定与约束） | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0 | 裁决: **Accepted**
> 上游: 铁律 v2.0 §0.6（场景只编排不生产）/§3.3/§3.5 + 湖规 v2.0 §一（`APPLICATION` 生产者=聚合管道、消费者=场景工作台）+ 《数据库访问规范》v1.2 ST07/ST08/ST09/MC02 + ADR-9/ADR-3
> 状态: 生效，全工程强制

---

## 1. 背景

PRD-09 REQ-FC-02 定义「确定性预测运行服务」：`环节额 = 合同基数 × 归属比例 × 实现率`，输出利润瀑布、上下区间、逐项目下钻，并要求**相同 `asOfTime` + 相同输入重跑得到相同明细**（REQ-FC-03 六要素：`forecastRunId` + `caliberId` + `asOfTime` + 输入快照 + 公式 + 证据引用）。

需求检视发现它同时踩三条线：

| 冲突 | 条款 |
|:--|:--|
| 计算主体放在 workspace（场景侧） | 铁律 §0.6「场景不生产能力，只编排能力」 |
| 金额明细结果必须落库 | 铁律 §3.3 / ADR-9「cognitive 不新增 DB 表」「推理结果不落盘」被广泛误读为"一切都不许存" |
| 结果表若落引擎控制 schema（`ecos_cognitive`/`ecos_knowledge`） | ST08「业务/外部数据只准落业务域载体」= FAIL |

用户在 Q3 中选择"落 cognitive/dccheng 之一"，因此本 ADR 必须回答三问：**哪个引擎**、**数据落在哪**、**怎么写进去**。

## 2. 决策

### 2.1 计算主体 = cognitive-engine（木·C），不是 kb-engine（水·K）

| 判据 | cognitive | kb(dccheng) |
|:--|:--|:--|
| 输入侧 | K 层画像/假设版本 + D 层事实快照 → 转化方向 **K→C（cheng）** ✅ | 输入即自身存储内容，计算会让 K 层持有派生数值 ❌ |
| 既有职责 | 因果推理 / 情景推演 / 统计预测（`ForecastController` 已在 cognitive）✅ | 存储 / 检索 / RAG / 规则 CRUD，正在承担 A3 非结构化退出（REQ-DATA-08）负担 ❌ |
| PRD §4.3 复用边界 | 明确禁止 `ScenarioSimulatorServiceImpl` / `CausalReasonerService` 充当金额计算器——即"金额计算器"被预留为认知侧**新增的确定性组件** ✅ | 无对应预留位 ❌ |
| 认知三表邻近性 | 假设版本、证据、信念与计算同侧，`hypothesis.version` 可直接进入 `inputSnapshotRef` ✅ | 跨引擎取假设需 REST 往返 ❌ |

> **实现定位**：cognitive 内新增**确定性计算组件**（暂名 `DeterministicForecastCalculator`），与 `ScenarioSimulatorServiceImpl`（探索式 What-if 交互）、`CausalReasonerService`（事后解释层）**三者职责互斥且不得互相回退**。PRD §4.3 的禁令在此重述：金额计算不得走 RAG 基线，不得走 Agent 回退。

### 2.2 数据落点 = 业务域 `APPLICATION` 层，schema 与写通道归 data-engine

- 结果表（`fc_forecast_run` / `fc_forecast_result` / `fc_forecast_result_detail` / `fc_backtest_metric` 等）落 **`ecos_dw`**（ultimate 档可物化到 Doris∨ClickHouse，ADR-13）。
- **DDL 由 data-engine 持有**，迁移脚本仍入单源目录 `gateway/src/main/resources/db/migration/`（铁律 v2.0 §3.1 / Q7）。
- **写通道 = data-engine 的应用层写入 REST**（沿用湖规"聚合管道产出 APPLICATION"的既有语义，新增端点按只增不改原则设计），cognitive **经 REST 提交**，不直连 `ecos_dw`。
- **LLM 不参与**：金额由确定性计算产生，区间由画像/假设给出；AI 侧只能引用运行结果（REQ-AI-04），无来源数值判 FAIL。

### 2.3 场景侧（workspace）只持有什么

| 允许 | 禁止 |
|:--|:--|
| 创建/查询 run（编排）、持有 `forecastRunId` + `inputSnapshotRef` 引用 | 任何金额聚合、公式求值、区间计算 |
| 结果展示、下钻导航、情景复制的编排请求 | 自造口径或自行映射"哪个表对应哪个实体"（§0.5-1） |
| 沙盘 run 与正式 run 的**运行目的标记** | 在既有 `run_type` 列扩枚举值（G2-5，IR03）→ 新增 `run_purpose` 列 |

### 2.4 口径（caliber）主权 = ontology（金·I）

`caliberId` / 公式版本 / 生效期 / 会计映射属**语义层定义态**，由 ontology-engine 持有并治理（REQ-ONTO-02 口径元数据），cognitive 计算时**引用**版本而非复制定义；公式 DSL 校验也在 ontology 侧（填 G5 空白"口径主权归属"）。

## 3. 关键流程（控制流 + 数据流）

```
workspace  POST /business.scenarios/{id}/forecast-runs  {scope, caliberId@v, asOfTime, hypothesisVersion}
   │        （只编排：登记 run 意图，不计算）
   ▼
cognitive  1) 冻结 inputSnapshotRef = {D 事实资源定位符集合, I 指标/口径版本, K 画像/假设版本}
           2) DeterministicForecastCalculator：
                环节额 = 合同基数 × 归属比例 × 实现率(来自画像版本 P10/P50/P90)
                聚合 项目→部门→企业 × 月 × 环节；区间来自画像；精度 NUMERIC(18,2)
           3) 六要素校验：缺一 → run 状态 FAIL（禁 AI 文本替代）
           4) POST data-engine /application/datasets/{ds}/rows（REST 写入，非直连）
   ▼
data-engine  写 ecos_dw.fc_forecast_result(_detail) + 登记 td_data_resource(layer=APPLICATION)
   │        写操作发 Kafka ecos.audit（ST06）
   ▼
workspace / 前端  读结果（经 gateway，RLS+CLS+脱敏裁决，ADR-7），下钻至证据引用
```

**幂等（G4-6 补齐）**：幂等键 = `sha256(scope + caliberId@version + asOfTime + inputSnapshotRef)`，落 `fc_forecast_run.idempotency_key`（`VARCHAR(64)` + 唯一索引）；重复提交返回既有 `forecastRunId`（HTTP 200 + `X-Idempotent-Replay: true`），**不新建 run**。

## 4. 影响

| 维度 | 影响 |
|:--|:--|
| 需求追溯 | REQ-FC-02/03 主责册由 PRD-09 → **PRD-05（cognitive）**，落库通道归 **PRD-02（data-engine）**，端到端切片仍见 PRD-09；需求基线修订（任务 #26）同步 |
| 规则 | ADR-9 三档 → 四档（④ 确定性计算产物属业务域）；铁律 §0.6 加"确定性计算不得落 workspace"判定；G5 空白「口径主权」由 §2.4 关闭 |
| cognitive 表数 | 仍零新增控制域表；新增两张引擎表在 cognitive：`ecos_cognitive_mind` / `ecos_cognitive_mind_binding`（属 ADR-8 ②/③ 档，非本 ADR） |
| 违规存量 | `DiagnosticAgentService.java:80,101,122`（ai 直查 cognitive 表，C2）与本 ADR 的"经 data-engine REST"路径冲突，必须改造 |
| 性能 | ⚠️ 计算在 cognitive、写在 data-engine、读在 workspace，多两次 REST 往返；大批量运行委托 runtime-task 异步（REQ-FC-07），单次运行阈值在 05 册量化 |
| 三档 | standard：结果表 PG `ecos_dw`；ultimate：可物化列存。计算主体不随档变 |

## 5. 备选方案与拒绝理由

| 方案 | 拒绝理由 |
|:--|:--|
| 留 workspace（PRD 现状） | 破 §0.6；场景层变重；ArchUnit C2「五引擎不依赖 workspace」反向出现 workspace 持计算 |
| 落 data-engine 为主责 | 与 K 层画像/假设版本割裂，计算需跨引擎往返取假设；且 data-engine 定位是治理不是认知 |
| 落 kb-engine（dccheng） | 使 K 层持有派生金额，K/C 职责混叠；kb 正承担 A3 退出与向量实测 |
| 新建预测微服务 | 违反"后端模块基线（7 部署 JAR + 48 reactor 构建模块，2026-09-28 Q5 裁决更正原「13」表述）不新增 / 不新增容器"红线 |

## 6. 关联

ADR-9（四档口径）、ADR-8（模型资产）、ADR-3（单库 5+1）、ADR-13（列存）、《数据湖存储分层规范》v2.0 §一/§七A、详细设计 02/05/09 册。

## 7. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.0 | 首次成文：cognitive 主责 + 业务域 `APPLICATION` 落库 + data-engine 写通道 + 场景仅编排 + 口径主权归 ontology + 幂等键定义 | AI Agent（用户裁决 Q3） |
