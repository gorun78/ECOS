# PRD-03 ontology-engine 需求规格（分册 03）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.1（**v1.1（2026-09-29 批量批准定版）**：凭证 = [需求检视报告 §十四 14.1](需求检视报告-2026-09-28.md)（批准人 = 用户，依 **Q12** 不补分册 APPROVAL_RECORD）。本册两处更正：**§1.4-V1 按 R-6 ①**——**口径主权归 ontology**，`ecos_metric_definition` 为唯一语义入口（随 **R-4 a+b** 的 `V168`），删除"跨引擎读 workspace 口径服务"的反向依赖表述，PRD-09 FC-01 同步更正（该册 §1.1/§1.3 另按 **R-60 ②** 把 `ecos_fc_caliber` 降为引用视图、禁第五套版本+审批栈）；**§2.1 `caliber_snapshot_json` 按 MC02** 由 `JSONB` 更正为 `TEXT`（数据库访问规范 v1.2 受控 JSON 只准 TEXT）。**"已批准"仅指需求文本生效**：本册 V1~V3 门禁与 `V168` 落地实测为 0，DDL 只落**迁移脚本文件**不实跑库，改业务 Java 代码需逐项再授权（报告 §14.4）；未创建的测试类一律记"未执行"（Q13／铁律 :14））
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 覆盖: REQ-ONTO-01~04
> 模块: `ecos_backend/engine/ontology-engine`（buszhi:18083）

---

## 一、REQ-ONTO-01 领域语义发布能力（P0）

### 1.1 需求陈述

在既有 V52 worldmodel 通用元模型（对象类型/关系/指标定义）之上，支持发布**利润形成领域语义**：业务对象关系图 + 指标定义集，并可被 kb 抽取与预测计算消费。不新造元模型——复用提案系统与版本快照机制。

### 1.2 领域对象与关系注册清单（首个领域包 `biz-profit`）

| 对象类型 | 编码 | 关键属性 | 主数据来源 |
|---|---|---|---|
| 客户 | `Customer` | customer_id, name | 既有主数据 |
| 合同 | `Contract` | contract_id, customer_id, base_amount, currency, change_version, effective_from/to | PRD-09 FC-06 |
| 项目 | `Project` | project_id, contract_id, project_type, status | PRD-09 FC-06 |
| 部门/团队 | `Department` / `ProjectTeam` | dept_id, name, parent_id | 既有主数据 |
| 验收/回款/分包环节 | `AcceptanceStage` / `CollectionStage` / `SubcontractStage` | 映射 `ecos_biz_stage_fact.stage` 枚举 | PRD-02 表2 |
| 资源投入 | `ResourceInput` | 映射 `ecos_biz_resource_fact` | PRD-02 表3 |
| 三类成本 | `SalaryCost` / `ManagementCost` / `MarketingCost` | 映射 `ecos_biz_cost_fact.cost_category` | PRD-02 表4 |

**关系类型**（基数标注，供 C2 关系合法性校验消费）：

| 关系 | 源 → 目标 | 基数 |
|---|---|---|
| OWNS | Customer → Contract | 1:n |
| DERIVES | Contract → Project | 1:n |
| ATTRIBUTED_TO | Project → Department/ProjectTeam | n:m（比例见归属表） |
| HAS_STAGE | Project → *Stage | 1:n |
| CONSUMES | Project → ResourceInput | 1:n |
| INCURS | Project/Department → *Cost | 1:n |

### 1.3 指标定义集（9 项，逐项登记）

| 指标编码 | 名称 | 公式（口径引用） | 可加性 | 期间 | 单位 |
|---|---|---|---|---|---|
| M_ACCEPTANCE_AMT | 验收实现额 | Σ stage.amount WHERE stage=ACCEPTANCE | 可加 | 月 | 币种列 |
| M_COLLECTION_AMT | 回款实现额 | Σ stage.amount WHERE stage=COLLECTION | 可加 | 月 | 币种列 |
| M_SUBCONTRACT_AMT | 分包实现额 | Σ stage.amount WHERE stage=SUBCONTRACT | 可加 | 月 | 币种列 |
| M_FC_REVENUE | 管理预测收入 | 按 caliber 公式（含验收/回款/分包正负属性） | 可加 | 月 | 币种列 |
| M_COST_SALARY | 薪酬成本 | Σ cost(SALARY) + Σ resource(fte×rate) 去重规则见口径 | 可加 | 月 | 币种列 |
| M_COST_MGMT | 管理成本 | Σ cost(MANAGEMENT)，含分摊规则引用 | 可加 | 月 | 币种列 |
| M_COST_MKT | 市场成本 | Σ cost(MARKETING) | 可加 | 月 | 币种列 |
| M_FC_PROFIT | 管理预测利润 | M_FC_REVENUE − (M_COST_SALARY+M_COST_MGMT+M_COST_MKT) | 可加 | 月 | 币种列 |
| M_REALIZATION_RATE | 实现率 | amount / (contract_base × attribution_ratio) | 不可加（重算） | 月 | 比率 |
| M_COLLECTION_CYCLE | 回款周期 | 验收月→回款月差（月数，均值聚合） | 不可加 | 季/年 | 月 |

**登记字段（每指标必带）**：编码 / 名称 / 公式表达式 / 可加性（ADDITIVE/SEMI/NON）/ 期间粒度 / 币种 / **caliber_id + formula_version（REQ-ONTO-02）** / 血缘（引用的事实表列 + 上游指标）。

### 1.4 发布流程与校验

1. 走既有提案系统：提案 → 审批 → 自动 Draft → 执行 → 版本发布（ONT-409 乐观锁）；
2. 发布门禁（新增校验，在提案执行前）：
   - V1：指标必带 caliber_id 且该口径版本状态=APPROVED（**v1.1 按 R-6 ① 更正**：口径与指标的**语义主权在本体引擎**——`ecos_metric_definition` 是**唯一语义入口**（R-4 a+b，随 `V168`），故 V1 校验**在本引擎内闭环**，**禁止反向跨引擎去读 workspace 的"口径服务"**；PRD-09 FC-01 的"财务口径"是**本册口径实体的一种业务实例登记**，其版本/审批复用本册既有提案-审批-发布栈（R-60 ② 禁第五套））；
   - V2：单位/币种一致性——公式引用指标的单位可推导且不冲突（如金额±金额=金额；金额/金额=比率）；
   - V3：血缘引用的事实表列在 DW 层存在（调 datanet CURATED 元数据，映射必校验铁律 §0.5-5）；
   - 任一不过 → 提案拒绝执行，返回具体违反项。
3. 领域包版本化：`biz-profit` 包整体一个 ontology 版本号，变更即新版本快照（kb `kb_ontology_snapshot` 消费）。

### 1.5 验收标准

1. `biz-profit` 领域包（7 类对象 + 6 关系 + 10 指标）经提案发布成功，快照可查；
2. V1~V3 门禁各有 1 正 1 反用例：无口径指标发布被拒、单位冲突被拒、血缘列不存在被拒；
3. kb 抽取消费该本体：C1 类型存在性以新快照 entity_codes 为准（联动 PRD-04）。

---

## 二、REQ-ONTO-02 指标口径元数据（P0）

### 2.1 模型扩展

指标定义实体新增字段（只加列）：

| 字段 | 类型 | 约束 |
|---|---|---|
| `caliber_id` | VARCHAR(36) | 发布态必填（Draft 可空） |
| `formula_version` | VARCHAR(20) | 与 caliber 内公式版本对应 |
| `caliber_snapshot_json` | TEXT | 发布时冻结口径公式副本（防口径后续变更破坏历史可解释性）；**v1.1 按 MC02 更正**：受控 JSON 只准 `TEXT`，原写 `JSONB` 违反数据库访问规范 v1.2 |

### 2.2 规则

1. **无口径或单位不一致的指标不能发布**（V1/V2 门禁，见 1.4）；
2. 口径升版不自动改指标——指标须走提案显式绑定新版本（旧预测按旧口径复现依赖 caliber_snapshot_json）；
3. `caliber_snapshot_json` 在发布时刻写入，之后只读。

### 2.3 验收

1. 发布校验拦截无口径指标（400 + 明确消息）；
2. 口径 v2 发布后，绑定 v1 的指标快照仍为 v1 公式；
3. 预测运行按快照公式复现（联动 PRD-09 FC-01 验收）。

---

## 三、REQ-ONTO-03 实体映射契约稳定性（P0，守护型）

### 3.1 冻结契约（唯一入口 `GET /api/v1/ontology/entity-mappings?ontologyId=`）

**响应字段集冻结（只增不改）**：

```json
{
  "code": 0,
  "data": [{
    "entityCode": "Project",
    "datasetId": "ds-uuid",
    "resourceName": "ecos_biz_stage_fact",
    "materialized": "true",
    "fieldMappings": [{"entityField": "project_id", "physicalColumn": "project_id", "transform": null}]
  }]
}
```

### 3.2 兼容规则

1. 新增字段允许（kb 侧忽略未知字段）；删除/改名/改语义 = 违约（CI 契约测试拦截）；
2. `materialized` 裁度语义不变：null/空/"true"/"1"=true，其他=skip（issue Q2_SKIP）；
3. 映射数据不落 kb 表（kb 仅内存装配）——本体改映射 → 下次抽取即生效。

### 3.3 契约测试用例（PMO-73 G3-T2）

| # | 用例 | 期望 |
|:--:|---|---|
| 1 | 改映射（换 physicalColumn）→ kb 重抽取 | 新列生效，C4 三道闸按新映射校验 |
| 2 | 映射指向不存在 DW 列 | kb 侧 INVALID_MAPPING issue，不实例化 |
| 3 | 响应 schema diff 基线 | 字段集只增（ecos-kb scan 产物比对） |

---

## 四、REQ-ONTO-04 Function 沙箱审计完备（P1）

### 4.1 规格

1. 既有红线"无审计日志的 Function 执行拒收"保持；补齐审计落 **Kafka `ecos.audit`**（替代/并存本地日志，事件 schema 见 PRD-01 §3.1）：
   - eventType=`FUNCTION_EXEC`；detail={functionId, expression(截断 512), objectType, params, resultSummary(单值), durationMs, cacheHit}；
2. 拒绝路径：审计发送失败（Kafka down）→ log 兜底**且允许执行**（与写操作审计口径一致，不阻断）；但**审计上下文缺失**（无 operator/traceId）→ 拒收执行（400）；
3. 沙箱边界不变：受限 SQL 聚合子集、单表、5s 超时、Caffeine 300s、本体变更主动失效。

### 4.2 验收

1. 无审计上下文的执行请求 → 400 拒收；
2. 正常执行 → Kafka/sysman 库可查 FUNCTION_EXEC 事件（含 traceId）；
3. 杀 Kafka → 执行不阻断 + log 兜底可查。

---

## 五、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| ONTO-01 | PRD-02 DATA-01 事实表、PRD-09 FC-01（**口径主权在本册**，FC-01 只做业务实例登记，R-6 ①） | KB 抽取、FC-02 指标版本快照 | 场景批次 A |
| ONTO-02 | FC-01 | ONTO-01 V1 门禁 | 场景批次 A |
| ONTO-03 | — | kb C4、PMO-73 G3-T2 契约测试 | **PMO-73 G3**（守护） |
| ONTO-04 | Kafka audit 通道 | — | PMO-73 G5 |

<!-- PRD-03-ontology-engine需求规格 / 2026-09-28 / v1.0 -->
