# ECOS 数据引擎 V2 — 需求规格说明书（PRD）

> 版本: v2.0-draft | 日期: 2026-07-11 | 作者: ECOS-PMO
> 遵循 PDCA 原则，协同 PM/ARCH/BE/FE/QA 五角色

---

## 一、背景与目标

当前数据引擎 V1 已完成 Controller→Service 分层，支持数据源管理、元数据采集、数据目录、分类管理、引擎自检五大功能。

V2 目标：将数据引擎升级为**企业级数据操作平台**，新增四大核心能力：

| # | 能力 | 一句话描述 |
|---|------|-----------|
| 1 | 统一数据访问层 | 一个 SQL 控制台打通所有异构数据源 |
| 2 | 数据同步任务 | 声明式源→目标数据搬家，支持全量/增量/CDC |
| 3 | 数据管道任务 | DAG 可视化编排多步 ETL 转换 |
| 4 | 数据质量/清洗 | 6维度质量规则 + 自动清洗 + 巡检报告 |

---

## 二、四大能力详细需求

### 2.1 统一数据访问层 (Unified Query)

**用户故事**：作为数据分析师，我想在一个 SQL 控制台里查询任何已注册的数据源，而不需要切换工具。

**功能清单**：
- [ ] SQL 编辑器（Monaco Editor，语法高亮 + 自动补全）
- [ ] Schema 浏览器（左侧树：数据源 → Schema → 表 → 字段）
- [ ] 查询历史（localStorage + 后端持久化）
- [ ] 结果展示（分页表格 + 导出 CSV/Excel）
- [ ] 查询超时控制 + 取消
- [ ] 参数化查询模板（保存为"查询片段"）

**DSL 描述**：
```yaml
# 查询模板 DSL (saved as .ecosql)
name: "月度营收统计"
datasource: ds-postgres-prod
sql: |
  SELECT date_trunc('month', created_at) AS month,
         SUM(amount) AS revenue
  FROM orders
  WHERE created_at >= :start_date AND created_at < :end_date
  GROUP BY 1 ORDER BY 1
params:
  start_date: {type: date, default: "2026-01-01"}
  end_date: {type: date, default: "2026-06-30"}
timeout: 30s
maxRows: 10000
```

**后端端点**：
```
POST   /api/v1/engine/data/query/execute        — 执行 SQL
GET    /api/v1/engine/data/query/schema/{dsId}   — 获取 Schema 树
POST   /api/v1/engine/data/query/template        — 保存查询模板
GET    /api/v1/engine/data/query/templates       — 查询模板列表
GET    /api/v1/engine/data/query/history         — 查询历史
```

---

### 2.2 数据同步任务 (Sync Task)

**用户故事**：作为数据工程师，我想定义一个从 MySQL 到 PostgreSQL 的定时同步任务，支持增量更新，失败时自动重试。

**功能清单**：
- [ ] 同步任务 CRUD（源→目标定义）
- [ ] 同步策略：FULL（全量覆盖）/ INCREMENTAL（按时间戳增量）/ CDC（Change Data Capture）
- [ ] 字段映射（源字段 → 目标字段，支持表达式转换）
- [ ] Cron 调度 + 手动触发
- [ ] 冲突解决策略：UPSERT / SKIP / OVERWRITE
- [ ] 重试策略（最大重试次数 + 退避）
- [ ] 执行日志 + 进度监控
- [ ] 前置/后置钩子（pre-sync SQL / post-sync SQL）

**DSL 描述**：
```yaml
# 同步任务 DSL (sync-task.ecos.yaml)
apiVersion: ecos/v1
kind: SyncTask
metadata:
  name: "订单数据同步"
  description: "每小时从 MySQL 同步订单到 PG 数仓"
spec:
  source:
    datasourceId: ds-mysql-prod
    table: orders
    where: "updated_at > :last_sync_time"  # 增量条件
  target:
    datasourceId: ds-postgres-prod
    table: dw_orders
    schema: public
  strategy: INCREMENTAL
  incrementalColumn: updated_at
  fieldMapping:
    - source: order_id       → target: order_id
    - source: customer_name  → target: customer_name
    - source: amount         → target: "amount * 1.0"  # 表达式
    - source: status         → target: order_status
      transform: "UPPER(:value)"
  conflictResolution: UPSERT
  primaryKeys: [order_id]
  schedule: "0 * * * *"          # 每小时
  retry:
    maxAttempts: 3
    backoffSeconds: 60
  hooks:
    preSync: "TRUNCATE dw_orders_staging"
    postSync: "REFRESH MATERIALIZED VIEW dw_orders_monthly"
  timeout: 600s
```

**后端端点**：
```
POST   /api/v1/engine/data/sync/tasks             — 创建同步任务
GET    /api/v1/engine/data/sync/tasks              — 列出同步任务
GET    /api/v1/engine/data/sync/tasks/{id}         — 查看同步任务详情
PUT    /api/v1/engine/data/sync/tasks/{id}         — 更新同步任务
DELETE /api/v1/engine/data/sync/tasks/{id}         — 删除同步任务
POST   /api/v1/engine/data/sync/tasks/{id}/run     — 手动触发执行
GET    /api/v1/engine/data/sync/tasks/{id}/runs    — 执行历史
GET    /api/v1/engine/data/sync/runs/{runId}       — 单次执行详情+日志
POST   /api/v1/engine/data/sync/tasks/{id}/pause   — 暂停
POST   /api/v1/engine/data/sync/tasks/{id}/resume  — 恢复
POST   /api/v1/engine/data/sync/validate           — 校验 DSL 合法性
```

---

### 2.3 数据管道任务 (Pipeline Task)

**用户故事**：作为数据工程师，我想通过可视化 DAG 编排多步数据转换流程（抽取→清洗→聚合→写入），并监控每一步的执行状态。

**功能清单**：
- [ ] 可视化 DAG 编辑器（React Flow 节点连线）
- [ ] 算子库（8 种内置算子）：
  - `extract` — 从数据源抽取
  - `filter` — 行过滤（WHERE 条件）
  - `transform` — 列变换（表达式/SQL）
  - `join` — 双表关联
  - `aggregate` — 分组聚合
  - `sort` — 排序
  - `limit` — 限制行数
  - `load` — 写入目标
- [ ] 管道任务 CRUD
- [ ] 定时调度 + 手动触发
- [ ] 分段执行 + 断点续跑
- [ ] 执行 DAG 可视化（节点颜色：pending/running/success/failed）
- [ ] 中间结果预览（每个算子节点的输出抽样）

**DSL 描述**：
```yaml
# 管道 DSL (pipeline.ecos.yaml)
apiVersion: ecos/v1
kind: PipelineTask
metadata:
  name: "客户RFM分析管道"
spec:
  schedule: "0 2 * * *"
  steps:
    - id: extract_orders
      type: extract
      config:
        datasourceId: ds-postgres-prod
        sql: "SELECT * FROM orders WHERE created_at >= NOW() - INTERVAL '1 year'"
      outputs: [filter_active]

    - id: filter_active
      type: filter
      config:
        condition: "status IN ('completed', 'shipped')"
      inputs: [extract_orders]
      outputs: [calc_rfm]

    - id: calc_rfm
      type: aggregate
      config:
        groupBy: [customer_id]
        aggregations:
          recency: "MAX(created_at)"
          frequency: "COUNT(*)"
          monetary: "SUM(amount)"
      inputs: [filter_active]
      outputs: [score_rfm]

    - id: score_rfm
      type: transform
      config:
        expressions:
          r_score: "NTILE(5) OVER (ORDER BY recency DESC)"
          f_score: "NTILE(5) OVER (ORDER BY frequency)"
          m_score: "NTILE(5) OVER (ORDER BY monetary)"
          rfm_segment: "CASE WHEN r_score>=4 AND f_score>=4 THEN 'VIP' ... END"
      inputs: [calc_rfm]
      outputs: [load_result]

    - id: load_result
      type: load
      config:
        datasourceId: ds-postgres-prod
        table: dw_customer_rfm
        mode: OVERWRITE
      inputs: [score_rfm]
  timeout: 1800s
```

**后端端点**：
```
POST   /api/v1/engine/data/pipeline/tasks          — 创建管道任务
GET    /api/v1/engine/data/pipeline/tasks           — 列出管道任务
GET    /api/v1/engine/data/pipeline/tasks/{id}      — 查看管道任务详情
PUT    /api/v1/engine/data/pipeline/tasks/{id}      — 更新管道任务
DELETE /api/v1/engine/data/pipeline/tasks/{id}      — 删除管道任务
POST   /api/v1/engine/data/pipeline/tasks/{id}/run  — 触发执行
GET    /api/v1/engine/data/pipeline/tasks/{id}/runs — 执行历史
GET    /api/v1/engine/data/pipeline/runs/{runId}    — 单次执行详情
GET    /api/v1/engine/data/pipeline/runs/{runId}/steps/{stepId}/preview — 中间结果预览
POST   /api/v1/engine/data/pipeline/validate        — 校验 DSL
```

---

### 2.4 数据质量/清洗任务 (Quality & Cleansing)

**用户故事**：作为数据治理员，我想定义数据质量规则（完整度/唯一性/准确度等），自动巡检并生成质量报告，对低质量数据自动清洗修复。

**功能清单**：
- [ ] 质量规则定义（6 维度）：
  - **完整性** Completeness — 非空率
  - **唯一性** Uniqueness — 重复率
  - **准确性** Accuracy — 值域合规率
  - **一致性** Consistency — 跨表/跨字段一致性
  - **及时性** Timeliness — 数据新鲜度
  - **有效性** Validity — 格式/类型合规率
- [ ] 质量巡检任务（按 Cron 定时 + 手动触发）
- [ ] 质量报告（Dashboard：总体评分 + 6 维度雷达图 + 明细列表）
- [ ] 清洗规则（标准化/去重/填充/验证/替换）
- [ ] 清洗任务（执行清洗并记录变更日志）
- [ ] 告警规则（质量低于阈值时推送通知）

**DSL 描述**：
```yaml
# 质量规则 DSL (quality-rule.ecos.yaml)
apiVersion: ecos/v1
kind: QualityRule
metadata:
  name: "订单数据质量检查"
  datasourceId: ds-postgres-prod
  table: orders
spec:
  rules:
    - name: "订单ID完整性"
      dimension: completeness
      column: order_id
      threshold: 100.0        # 期望 100% 非空
      severity: block

    - name: "订单ID唯一性"
      dimension: uniqueness
      column: order_id
      threshold: 100.0
      severity: block

    - name: "金额有效性"
      dimension: validity
      column: amount
      condition: "amount > 0 AND amount < 1000000"
      threshold: 99.9
      severity: warn

    - name: "状态值域"
      dimension: accuracy
      column: status
      allowedValues: [pending, confirmed, shipped, completed, cancelled]
      threshold: 100.0
      severity: block

    - name: "数据及时性"
      dimension: timeliness
      column: created_at
      maxAge: 24h
      threshold: 95.0
      severity: warn

  schedule: "0 6 * * *"      # 每天早上 6 点巡检
  alerting:
    enabled: true
    minScore: 80              # 总分低于 80 分告警
    channels: [wecom, email]
```

```yaml
# 清洗规则 DSL (cleanse-rule.ecos.yaml)
apiVersion: ecos/v1
kind: CleanseRule
metadata:
  name: "订单数据清洗"
  datasourceId: ds-postgres-prod
  table: orders
spec:
  rules:
    - name: "状态标准化"
      column: status
      action: standardize
      mapping:
        "已完成": "completed"
        "已发货": "shipped"
        "待处理": "pending"

    - name: "手机号去重"
      column: phone
      action: deduplicate
      keepStrategy: first

    - name: "空值填充"
      column: region
      action: fill_default
      defaultValue: "未知"

    - name: "邮箱验证"
      column: email
      action: validate
      pattern: "^[\\w.-]+@[\\w.-]+\\.\\w+$"
      invalidAction: flag       # flag | remove | replace
      replaceValue: null

  backupBeforeCleanse: true
  schedule: "0 7 * * *"
```

**后端端点**：
```
# 质量规则
POST   /api/v1/engine/data/quality/rules
GET    /api/v1/engine/data/quality/rules
PUT    /api/v1/engine/data/quality/rules/{id}
DELETE /api/v1/engine/data/quality/rules/{id}
POST   /api/v1/engine/data/quality/rules/{id}/run    — 执行巡检
GET    /api/v1/engine/data/quality/rules/{id}/reports — 巡检历史
GET    /api/v1/engine/data/quality/reports/{reportId} — 报告详情
GET    /api/v1/engine/data/quality/dashboard          — 质量总览

# 清洗规则
POST   /api/v1/engine/data/cleanse/rules
GET    /api/v1/engine/data/cleanse/rules
PUT    /api/v1/engine/data/cleanse/rules/{id}
DELETE /api/v1/engine/data/cleanse/rules/{id}
POST   /api/v1/engine/data/cleanse/rules/{id}/run    — 执行清洗
GET    /api/v1/engine/data/cleanse/rules/{id}/runs   — 清洗历史
GET    /api/v1/engine/data/cleanse/runs/{runId}/log  — 变更日志
```

---

## 三、共通设计要求

### 3.1 任务状态机

```
PENDING → QUEUED → RUNNING → SUCCEEDED
                            → FAILED → (RETRYING → RUNNING)
                            → CANCELLED
                            → TIMED_OUT
```

### 3.2 数据库表设计（新增）

| 表名 | 用途 |
|------|------|
| `ecos_query_template` | 查询模板 |
| `ecos_query_history` | 查询历史 |
| `ecos_sync_task` | 同步任务定义 |
| `ecos_sync_run` | 同步执行记录 |
| `ecos_pipeline_task` | 管道任务定义 |
| `ecos_pipeline_step` | 管道步骤定义 |
| `ecos_pipeline_run` | 管道执行记录 |
| `ecos_pipeline_step_run` | 步骤执行记录 |
| `ecos_quality_rule` | 质量规则定义 |
| `ecos_quality_report` | 质量巡检报告 |
| `ecos_quality_issue` | 质量问题明细 |
| `ecos_cleanse_rule` | 清洗规则定义 |
| `ecos_cleanse_run` | 清洗执行记录 |
| `ecos_cleanse_change_log` | 清洗变更日志 |

### 3.3 前端组件树

```
数据工作台
├── 统一查询 (SQL Console)
│   ├── SchemaTree (左侧)
│   ├── MonacoEditor (中间上方)
│   ├── ResultTable (中间下方)
│   └── TemplateManager (右侧面板)
├── 同步任务
│   ├── SyncTaskList (列表页)
│   ├── SyncTaskEditor (编辑页: 源→目标 + 字段映射表)
│   └── SyncRunMonitor (运行监控)
├── 管道任务
│   ├── PipelineList
│   ├── PipelineDAGEditor (React Flow 可视化编辑)
│   └── PipelineRunMonitor (DAG 状态图)
└── 数据质量
    ├── QualityDashboard (总览仪表盘)
    ├── QualityRuleEditor (规则编辑)
    ├── CleanseRuleEditor (清洗规则编辑)
    └── QualityReport (报告详情)
```

---

## 四、分阶段交付（PDCA）

| Sprint | 内容 | P | D | C | A |
|--------|------|---|---|---|---|
| **Sprint 2.1** | 统一查询 + Schema 浏览器 | ← 当前 | | | |
| **Sprint 2.2** | 同步任务（全量 + 增量） | | | | |
| **Sprint 2.3** | 管道任务（DAG 编排） | | | | |
| **Sprint 2.4** | 数据质量（规则 + 巡检） | | | | |
| **Sprint 2.5** | 数据清洗（规则 + 执行） | | | | |
| **Sprint 2.6** | 全链路集成 + E2E | | | | |

---

## 五、下一步行动

- [ ] **ARCH**：确认表设计、Service 接口、与现有 ConnectorFactory 的集成方式
- [ ] **BE**：按 Sprint 2.1 开始实现统一查询功能
- [ ] **FE**：开始前端 SQL Console 页面开发
- [ ] **PMO**：审核 DSL 设计，确认 API 契约
