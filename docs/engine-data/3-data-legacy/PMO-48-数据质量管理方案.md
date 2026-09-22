# PMO-48: 数据质量管理方案（DQ Governance Blueprint）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣
> 日期: 2026-09-10
> 状态: **REVISION 2 (已按 §12 7 项决策修订)**
> 铁律:
>   1. 数据质量规则落地 data-engine，复用 kb-engine 完整生命周期模式（DRAFT→IN_REVIEW→ACTIVE→DEPRECATED→SUPERSEDED + 版本快照）
>   2. DQ 告警必须接到 `runtime-core` 告警服务，禁止在 data-engine 自建监控（架构铁律 2.5 #4）
>   3. 基础设施访问（PG 查询样本）统一走 runtime-access `IStorageAdapter`（已在 QualityServiceImpl 验证可行）
>   4. 敏感 DQ 检查项（手机号/身份证号/银行卡号/金额）响应前调 security-engine `POST /api/security/mask` 脱敏
>   5. 不新增 Maven 模块、不新增 Docker 容器；新表以 Flyway 迁移新增（schema 只加不删）

---

## §0. 现状基线（调研结论摘要）

### 0.1 后端 DQ 实现盘点

| 维度 | 现状 | 关键文件 |
|:--|:--|:--|
| **规则 CRUD（旧）** | 表 `ecos_dq_rule_v2`，5 类 ruleType（NOT_NULL/UNIQUE/FORMAT/RANGE/CONSISTENCY），无分类、无生命周期、无版本 | [DqController.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/controller/DqController.java) |
| **规则评估引擎（新）** | 表 `ecos_quality_rule`/`ecos_quality_evaluation`，支持 7 类规则（NOT_NULL/NOT_EMPTY/RANGE/REGEX/UNIQUE/LENGTH/CUSTOM），样本评估 | [QualityController.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/QualityController.java) + [QualityServiceImpl.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/QualityServiceImpl.java) |
| **周期自检** | `DqScheduledTask` 每天 08:00 cron，已接入 runtime-task | [DqScheduledTask.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/scheduler/DqScheduledTask.java) |
| **合规规则体系** | kb-engine 已有完整 5 态生命周期 + `sys_rule_version` 版本快照 + `domain` 业务域字段 | [ComplianceRule.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/engine/kb-engine/kb-engine-api/src/main/java/com/chinacreator/gzcm/engine/kb/model/ComplianceRule.java) |
| **告警系统** | `runtime-core` 内存告警（含 P0-P3 级别）无 DB 持久化；DQ 告警走 evolution/trigger 未接 IAlertService | [IAlertService.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/alert/IAlertService.java) |
| **监控仪表盘** | `MonitorService.countOpenDqIssues()` 已查 `ecos_dq_issue`，DQ 监控天然挂载点 | [MonitorService.java](file:///D:/workspace/javaprojects/ECOS/ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/service/MonitorService.java) |

### 0.2 前端 DQ 实现盘点

已就绪但**未接入路由**的能力（直接可消费）：
- [RuleTemplateLibrary.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/RuleTemplateLibrary.tsx) — 5 模板 × 两步向导
- [api.ts](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/api.ts) L1399-1449 — DQ Dashboard 9 函数
- `db.*` i18n key 62 个已预留

未接入项：
- `/dq_dashboard` 路由在 [main.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/main.tsx) 未注册（Topbar / App / ContextMenu 三处引用都跳 404）
- 仅渲染的 DQ 页是 [HealthTab.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/tabs/HealthTab.tsx)

### 0.3 当前问题诊断

1. **双轨规则系统**：`DqController` 与 `QualityController` 是两套独立实现，规则表与评估表不同源
2. **规则无分类与生命周期**：仅 `ruleType` + `severity`，无 domain/category/status/version
3. **告警未收敛**：DQ 告警走 evolution 触发，未进 runtime 告警链路
4. **前端 DQ Dashboard 路由缺位**：UI 入口存在但落 404
5. **评估策略 SPI 未对外暴露**

---

## §1. 总体蓝图

```
┌────────────────────────────────────────────────────────────────┐
│                        ECOS 数据质量治理                        │
├────────────────────────────────────────────────────────────────┤
│  用户/治理层                                                     │
│   DQ 仪表盘 / 规则中心 / 评估中心 / 告警中心 / 知识库 / 工单中心 │
├────────────────────────────────────────────────────────────────┤
│  data-engine（土·D，18082）                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ rules → lifecycle → checks → alerts → score → report    │ │
│  └──────────────────────────────────────────────────────────┘ │
├────────────────────────────────┬───────────────────────────────┤
│ kb-engine（金/水，18083/18086）|
│  · sys_compliance_rule（domain + 版本快照）可整体复用          │
├────────────────────────────────────────────────────────────────┤
│ cognitive-engine（木·C，18089）                       │
│  · 根因分析引擎（基于因果链>3层）                │
├────────────────────────────────────────────────────────────────┤
│ security-engine（护，18081）                          │
│  · mask / RLS / CLS / audit/log                          │
├────────────────────────────────────────────────────────────────┤
│ runtime（器，横切底座）                                  │
│  · runtime-core IAlertService（P0-P3）           │
│  · runtime-task（DQ 周期调度，已用）             │
│  · runtime-monitor（已打通 DQ 问题数）             │
│  · llm-gateway（DQ 报告摘要 / RCA 摘要）           │
├────────────────────────────────────────────────────────────────┤
│ 存储层                                                        │
│  PostgreSQL sys_man（schema `ecos_dq`）                          │
│  MinIO（DQ 报告 PDF/HTML 归档）                                │
└────────────────────────────────────────────────────────────────┘
```

**贯穿约束**：
- 所有 DQ 写操作走全局审计（铁律 2.4 #5）
- 数据主存储 PG `ecos_dq` schema，超大报告走 MinIO
- DQ 不向 cognitive/kb 新增 DB 表（铁律 2.3/3.3）

---

## §2. 模块 1：规则体系

### 2.1 设计目标

- 规则三分类：业务规则 / 技术规则 / 合规规则
- 状态机生命周期：DRAFT → IN_REVIEW → ACTIVE → (DEPRECATED / SUPERSEDED / REJECTED)
- 版本快照：每次发布生成 snapshot
- 来源：手工 / kb-engine compliance 同步 / 模板库导入

### 2.2 数据模型

新表（Flyway V110+）：

```sql
-- 规则主表（取代 ecos_dq_rule_v2 和 ecos_quality_rule 双轨）
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule (
    id                  VARCHAR(64) PRIMARY KEY,
    rule_name           VARCHAR(255) NOT NULL,
    rule_code           VARCHAR(191) UNIQUE,
    category            VARCHAR(32) NOT NULL,    -- BUSINESS/TECHNICAL/COMPLIANCE
    domain              VARCHAR(128),
    rule_type           VARCHAR(32) NOT NULL,
    severity            VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    target_kind         VARCHAR(32) NOT NULL,    -- DATASOURCE/TABLE/FIELD/PIPELINE
    target_id           VARCHAR(64),
    target_table        VARCHAR(191),
    target_field        VARCHAR(191),
    target_pipeline_id  VARCHAR(64),
    parameters          JSONB NOT NULL DEFAULT '{}',
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version             INT NOT NULL DEFAULT 1,
    approved_by         VARCHAR(128),
    effective_date      BIGINT,
    expiry_date         BIGINT,
    source_type         VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    source_ref          VARCHAR(64),
    created_by          VARCHAR(128),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(128),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_by          VARCHAR(128),
    deleted_at          TIMESTAMP,
    create_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dq_rule_category ON ecos_dq.dq_rule(category) WHERE is_deleted = FALSE;
CREATE INDEX idx_dq_rule_status   ON ecos_dq.dq_rule(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_dq_rule_target   ON ecos_dq.dq_rule(target_kind, target_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_dq_rule_domain   ON ecos_dq.dq_rule(domain) WHERE is_deleted = FALSE;

-- 规则版本快照
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule_version (
    id                  VARCHAR(64) PRIMARY KEY,
    rule_id             VARCHAR(64) NOT NULL,
    version_number      INT NOT NULL,
    snapshot            JSONB NOT NULL,
    changed_by          VARCHAR(128),
    changed_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    change_note         TEXT,
    UNIQUE (rule_id, version_number)
);
CREATE INDEX idx_dq_rule_version_rule ON ecos_dq.dq_rule_version(rule_id, version_number DESC);
```

### 2.3 规则类型矩阵

| 类别 | rule_type | 含义 |
|:--|:--|:--|
| 完整性 | `NOT_NULL`/`NOT_EMPTY`/`ROW_COUNT` | 非空/非空串/行数阈值 |
| 唯一性 | `UNIQUE` | 字段/复合键无重复 |
| 有效性 | `RANGE`/`REGEX`/`LENGTH`/`FORMAT` | 值域/正则/长度/标准格式 |
| 一致性 | `CONSISTENCY` | 跨表关联 |
| 及时性 | `FRESHNESS` | 数据延迟 |
| 准确性 | `ACCURACY_QUERIES`/`ACCURACY_CRUD` | 自定义 SQL / CRUD 后值 |
| 业务 | `BUSINESS_LOGIC`/`ENUM_CONSTRAINT` | SpEL/枚举 |
| 合规 | `COMPLIANCE_SYNC`/`PII_PRESENCE` | kb-engine 同步 / PII 检测 |

### 2.4 规则生命周期 API

| Method | Path | 用途 |
|:--|:--|:--|
| `POST` | `/api/v1/dq/rules/draft` | 创建草稿 |
| `PUT` | `/api/v1/dq/rules/{id}` | 编辑草稿/已停用 |
| `POST` | `/api/v1/dq/rules/{id}/submit` | DRAFT → IN_REVIEW |
| `POST` | `/api/v1/dq/rules/{id}/approve` | IN_REVIEW → ACTIVE（version+1） |
| `POST` | `/api/v1/dq/rules/{id}/reject` | IN_REVIEW → REJECTED |
| `POST` | `/api/v1/dq/rules/{id}/deprecate` | ACTIVE → DEPRECATED |
| `POST` | `/api/v1/dq/rules/{id}/supersede` | ACTIVE → SUPERSEDED |
| `GET` | `/api/v1/dq/rules` | 列表（带分页/过滤） |
| `GET` | `/api/v1/dq/rules/{id}` | 详情 + 版本历史 |
| `GET` | `/api/v1/dq/rules/{id}/versions` | 版本快照列表 |
| `DELETE` | `/api/v1/dq/rules/{id}` | 逻辑删除 |
| `POST` | `/api/v1/dq/rules/sync-from-kb` | 从 kb-engine 同步 |

**实现栈**：
- 写层：`data-engine-impl` 的 `DqRuleServiceV2`（Lombok DTO + MyBatis Mapper）
- 旧 `DqController` 保留只读兼容层；新前端统一切到 `/api/v1/dq/rules`
- 旧 `QualityController` 的 evaluate 端点保留

### 2.5 前端落地

- 改造 [HealthTab.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/tabs/HealthTab.tsx) 为规则中心入口
- [RuleTemplateLibrary.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/RuleTemplateLibrary.tsx) 挂到规则中心顶部
- 新增 4 个子组件（每个 ≤ 300 行）：`RuleListTab` / `RuleDetailDrawer` / `RuleEditWizard` / `RuleReviewModal`
- i18n：在 `dw.*` 下扩展 `dw.dqRule.*`

### 2.6 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 5 工作日 |
| 前端开发 | 1 人 × 4 工作日 |
| DB 迁移 | 3 个 Flyway 脚本 |
| 测试 | 2 天 |

### 2.7 预期效果

- 规则可追溯（每次 approve 必产生 version snapshot）
- 规则可复用（kb-engine 合规规则一键同步）
- 规则可治理（按 domain 维度统计合规规则覆盖率）

---

## §3. 模块 2：监控体系

### 3.1 设计目标

- 实时检测（数据接入/更新触发）
- 周期检测（每日 08:00 + 自定义 cron）
- 全链路覆盖：接入 → 存储 → 处理 → 应用
- 频率与阈值可配置

### 3.2 检测触发矩阵

| 层级 | 触发源 | 检测动作 | 调度器 |
|:--|:--|:--|:--|
| 接入层 | 数据源新建/变更 | 连通性 + schema drift | runtime-task |
| 存储层 | pipeline 执行完成 / 行数突变 | 抽样评估 | PipelineEvent |
| 处理层 | pipeline 节点失败 | 新鲜度/空值检查 | EventBus |
| 应用层 | API 查询超出阈值 | 质量抽样（默认关闭） | 限流中间件 |

### 3.3 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_monitor_schedule (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(191) NOT NULL,
    rule_ids        JSONB NOT NULL,
    trigger_type    VARCHAR(32) NOT NULL,    -- SCHEDULE/EVENT/MANUAL
    cron_expression VARCHAR(64),
    event_pattern   VARCHAR(191),
    monitor_target  VARCHAR(64),
    max_runtime_seconds INT DEFAULT 60,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    is_deleted      BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_dq_monitor_enable ON ecos_dq.dq_monitor_schedule(trigger_type) WHERE enabled AND NOT is_deleted;

CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule_check (
    id              VARCHAR(36) PRIMARY KEY,
    rule_id         VARCHAR(64) NOT NULL,
    schedule_id     VARCHAR(64),
    trigger_type    VARCHAR(16) NOT NULL,
    executed_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    passed          BOOLEAN NOT NULL,
    total_rows      BIGINT DEFAULT 0,
    failed_rows     BIGINT DEFAULT 0,
    pass_rate       DOUBLE PRECISION,
    latency_ms      INT,
    error_message   TEXT,
    sample_size     INT,
    sample_failures JSONB
);
CREATE INDEX idx_dq_check_rule_time ON ecos_dq.dq_rule_check(rule_id, executed_at DESC);
CREATE INDEX idx_dq_check_time      ON ecos_dq.dq_rule_check(executed_at DESC);
```

### 3.4 检测执行流水线

```
触发 → DqScheduler.runRuleBatch(scheduleId)
    ↓
1. 取 schedule 关联的 ACTIVE 规则
2. runtime-task 派发任务 → 并发池（默认 4 并发）
3. 每条规则调 QualityRuleEvaluator.evaluate(rule)：
   - 取 td_datasource.connectionConfig
   - 经 IStorageAdapter 拉 sampleSize 行
   - 按 rule.parameters 计算
4. 写 ecos_quality_evaluation（已有表）
5. 计算规则级健康度（pass_rate）
6. 健康度低于阈值 → 创建 issue + 触发告警
7. 写 ecos_dq.dq_rule_check
```

### 3.5 监控频率预设

| 场景 | 频率 | 适用规则类型 |
|:--|:--|:--|
| 实时（CDC） | 数据变更 1 秒内 | 唯一性、范围、正则 |
| 高频 | 5 分钟窗口 | 新鲜度 |
| 常规 | 整点 | 完整性、空值 |
| 日检（每日 08:00，已存在） | 每日 | 全量巡检 |
| 周检（每周一 02:00） | 每周 | 一致性跨表 |
| 自定义 | cron 表达式 | 任意 |

### 3.6 阈值与动态基线

- 默认 100% 通过率（强约束）
- 配置化：`ecos_dq.dq_rule.parameters.threshold` 取值 0.0-1.0
- 动态基线：参考历史 7 次 pass_rate 计算 95th 分位作为偏离告警阈值

### 3.7 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 6 工作日 |
| 前端开发 | 1 人 × 3 工作日 |
| 测试 | 1 天 |

### 3.8 预期效果

- 数据接入异常 5 分钟内可发现
- Pipeline 失败自动触发关联检查（自动发现率 > 80%）
- 用户可自定义监控频率与阈值

---

## §4. 模块 3：评估体系

### 4.1 6 维度指标模型

| 维度 | 计算方式 | 数据来源 |
|:--|:--|:--|
| **准确性** Accuracy | `1 - (差异行数 / 总行数)` | `ACCURACY_QUERIES` + `ACCURACY_CRUD` |
| **完整性** Completeness | `(总行数 - NULL) / 总行数` | `NOT_NULL` / `NOT_EMPTY` / `ROW_COUNT` |
| **一致性** Consistency | `1 - (冲突行数 / 总行数)` | `CONSISTENCY` / `ENUM_CONSTRAINT` |
| **及时性** Freshness | `1 - (实际延迟 / 最大可接受延迟)` | `FRESHNESS` |
| **唯一性** Uniqueness | `1 - (重复行数 / 总行数)` | `UNIQUE` |
| **有效性** Validity | `(匹配行数 / 总行数)` | `REGEX` / `FORMAT` / `LENGTH` / `ENUM_CONSTRAINT` |

### 4.2 健康度评分

**资产级健康度**：
```
health = Σ (维度得分 × 维度权重) / Σ (维度权重)
默认权重：完整性 30 | 准确性 25 | 一致性 15 | 唯一性 15 | 有效性 10 | 及时性 5
（按 domain 自定义）
```

**资产等级**：

| 区间 | 等级 |
|:--|:--|
| 95-100 | A 优秀 |
| 85-94 | B 良好 |
| 70-84 | C 一般 |
| 50-69 | D 较差 |
| < 50 | F 不可用 |

**报告级健康度**：日=当日均值×0.6+基线×0.4；周=7日日均+趋势；月=30日滚动+维度达标率；季=90日+同比

### 4.3 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_score_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    dimension           VARCHAR(16) NOT NULL,
    scope_type          VARCHAR(16) NOT NULL,    -- FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM
    scope_id            VARCHAR(64) NOT NULL,
    score_value         DOUBLE PRECISION NOT NULL,
    weight              DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sample_size         INT,
    distinct_rule_count INT,
    evaluated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata            JSONB
);
CREATE INDEX idx_dq_score_scope_time ON ecos_dq.dq_score_snapshot(scope_type, scope_id, evaluated_at DESC);

CREATE TABLE IF NOT EXISTS ecos_dq.dq_score_asset (
    asset_type          VARCHAR(16) NOT NULL,
    asset_id            VARCHAR(64) NOT NULL,
    overall_score       DOUBLE PRECISION NOT NULL,
    grade               VARCHAR(1) NOT NULL,
    dimension_scores    JSONB NOT NULL,
    trend                JSONB,
    last_evaluated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (asset_type, asset_id)
);
CREATE INDEX idx_dq_score_asset_grade ON ecos_dq.dq_score_asset(grade) WHERE is_deleted = FALSE;
```

### 4.4 健康度计算服务

`DqScoreService`（data-engine-impl）：
- `recalculateDimension(ruleCheckId)` — 单次检测 → 维度分
- `recalculateAsset(assetType, assetId)` — 维度分 → 资产级
- `recalculateDomain(domainId)` — 资产级 → 域级
- 触发：每次 `dq_rule_check` 写入后 `@Async`
- 缓存：Caffeine（asset 级 5 分钟）+ PG 持久化

### 4.5 API

```
GET /api/v1/dq/scores?scopeType=TABLE&scopeId=xxx      当前资产 6 维雷达图
GET /api/v1/dq/scores/trend?scope=TABLE&scopeId&days=30 趋势
GET /api/v1/dq/scores/ranking?type=TABLE&grade=F        风险排行
GET /api/v1/dq/scores/system                            系统健康度
```

### 4.6 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 5 工作日 |
| 前端开发 | 1 人 × 3 工作日 |
| 测试 | 1 天 |

### 4.7 预期效果

- 每张表/字段有 B/L 等级（喂给资产目录徽章）
- 域级健康度作 KPI 看板
- 风险排行（F 级）一键跳转整改工单

---

## §5. 模块 4：告警与响应

### 5.1 分级策略

| 级别 | 触发条件 | SLO | 通知渠道 |
|:--|:--|:--:|:--|
| **P0** | CRITICAL 规则失败、PII 命中、脱敏字段违规 | 5 min | 企微@值班 + 短信 + 飞书 + 电话 |
| **P1** | HIGH 规则失败率 > 50% | 15 min | 企微 + 飞书群 |
| **P2** | MEDIUM 规则失败 | 1 h | 飞书群 + mail |
| **P3** | LOW/INFO 异常 | 24 h | 邮件日报 |

**升级**：P2 5min 未 ack → 升级 P1；P1 15min 未 ack → 升级 P0

### 5.2 告警数据模型

新建 `ecos_dq.dq_alert_record`（DB 持久化，runtime-core 内存告警不持久化无法兜底）：

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_alert_record (
    id            VARCHAR(36) PRIMARY KEY,
    rule_id       VARCHAR(64) NOT NULL,
    alert_level   VARCHAR(4) NOT NULL,    -- P0-P3
    alert_type    VARCHAR(32) NOT NULL,
    asset_id      VARCHAR(64),
    asset_name    VARCHAR(191),
    rule_name     VARCHAR(255),
    message       TEXT NOT NULL,
    payload       JSONB,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- PENDING/NOTIFIED/ACKED/RESOLVED/IGNORED/ESCALATED
    escalated_to  VARCHAR(4),
    notify_count  INT NOT NULL DEFAULT 0,
    last_notify_at TIMESTAMP,
    notify_channels JSONB,
    ack_by        VARCHAR(128),
    ack_at        TIMESTAMP,
    resolved_by   VARCHAR(128),
    resolved_at   TIMESTAMP,
    resolved_note TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dq_alert_status ON ecos_dq.dq_alert_record(status) WHERE status IN ('PENDING','NOTIFIED');
CREATE INDEX idx_dq_alert_time   ON ecos_dq.dq_alert_record(created_at DESC);
CREATE INDEX idx_dq_alert_level  ON ecos_dq.dq_alert_record(alert_level);
```

### 5.3 告警引擎实现

`DqAlertDispatcher`（data-engine-impl）：

```
触发：dq_rule_check.passed=false
  ↓
1. 计算 alert_level
2. 判重：同 rule_id + 5min 窗口 → notify_count++
3. 调 IAlertService.triggerAlert()  ← runtime-core（统一入口）
4. 异步 INSERT dq_alert_record（P0-P3 全量落库，保证审计溯源；铁律 2.4 #5 默认 DENY 内网下不可降级）
5. 通知推送（仅 P0/P1/P2 推送；P3 仅落库不通知，邮件日报消化）：
   - runtime-core 统一推送：DQ 侧只调 `IAlertService.triggerAlert(payload, channels)`
   - channels 由 runtime 订阅者注册（飞书/企微/短信/钉钉），DQ 侧不直连 IM API
   - 联系人路由：runtime 内部 IMonitorWarnContactService（已有），DQ 仅传 asset_id 让 runtime 决定
   - Kafka 分发：runtime-core 在 P0/P1/P2 推送后 publish topic `ecos.dq.alert`
     payload：{alertId, ruleId, assetId, severity, pass_rate, sample_failures[], timestamp}
     DQ 侧不直接操作 Kafka，仅声明订阅主题供下游消费
   - 敏感值脱敏：payload 中敏感字段值已先调 `securityEngine.mask()`，再交给 runtime
6. 升级：1min 扫 PENDING + notified_at > 5min（P2→P1, P1→P0）
```

**与 security-engine 集成**：
- 告警含敏感字段值 → 推送前调 `POST /api/security/mask` 脱敏
- 所有 dispatch 调 `POST /api/v1/security/audit/log` 异步写审计

**runtime 边界声明**：
- DQ 侧职责：触发判定、落库、传 payload、传 channels 偏好
- runtime 侧职责：通知渠道实现（飞书/企微/短信/钉钉/Kafka）、联系人路由、推送失败重试
- 禁止：DQ 侧自建 webhook / 客户端 / 自建 Kafka producer（违反铁律 2.5 #4「监控告警统一走 runtime-monitor」「补强而非自建」）

### 5.4 工单流转

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_work_order (
    id              VARCHAR(36) PRIMARY KEY,
    order_no        VARCHAR(64) UNIQUE NOT NULL,  -- WO-YYYYMMDD-XXX
    alert_id        VARCHAR(36) NOT NULL,
    rule_id         VARCHAR(64) NOT NULL,
    asset_id        VARCHAR(64),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- PENDING/ASSIGNED/IN_WORK/RESOLVED/VERIFIED/CLOSED/REJECTED
    handling_mode   VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    -- MANUAL/AUTO_REPAIR/EXEMPT
    assigned_to     VARCHAR(128),
    assigned_at     TIMESTAMP,
    repair_action   VARCHAR(64),
    repair_status   VARCHAR(16),
    repair_log      JSONB,
    verified_by     VARCHAR(128),
    verified_at     TIMESTAMP,
    verify_pass     BOOLEAN,
    verify_note     TEXT,
    resolved_by     VARCHAR(128),
    resolved_at     TIMESTAMP,
    resolution_note TEXT,
    preventive_actions JSONB,
    rca_result      JSONB,
    rca_confidence  DOUBLE PRECISION,
    rca_analyzed_at TIMESTAMP,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMP,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_wo_status ON ecos_dq.dq_work_order(status) WHERE is_deleted = FALSE;
CREATE INDEX idx_wo_alert ON ecos_dq.dq_work_order(alert_id);
CREATE INDEX idx_wo_asset ON ecos_dq.dq_work_order(asset_id) WHERE is_deleted = FALSE;
```

**状态机**：

```
PENDING ─assign→ ASSIGNED ─start→ IN_WORK ─solve→ RESOLVED ─verify→ VERIFIED ─close→ CLOSED
   │                                                  │
   │ 5min 无人认领 → 自动升级                          │ verify 失败 → 回 IN_WORK
   ↓                                                  ↓
ASSIGNED(下一人)                                    retry_count++（最多 3 次）→ 强制人工
```

### 5.5 自动修复场景（仅低危）

> **决策**：自动修复范围仅低危场景（§12 #5）。空值/格式/PII/一致性等中高危场景一律走人工工单。

| 告警类型 | 严重度 | 自动修复动作 | 风险 | 决策 |
|:--|:--|:--|:--|:--:|
| 数据源瞬时断连（数据源 ACTIVE 标记但健康检查失败） | P0 | 3 次重试（间隔 10s/30s/60s）→ 仍失败则写 ERROR 状态 + 落工单 | 低 | 自动 |
| Pipeline 节点失败且 nodeConfig.retry 已启用 | P1 | 按节点 retry 配置重跑 → 成功后触发成功事件 → 重跑该 pipeline 关联 ACTIVE 规则 | 低 | 自动（前提：已配 retry；未配则降级为人工工单） |
| 数据源 schema drift（新增列/列不存在） | P1 | 仅通知 + 历史告警聚合，不自动修复 schema | 低（只读） | 自动（聚合通知） |
| 空值（影响 < 100 行） | P2 | ❌ 自动不修复 | 中 | **人工工单** |
| 数据格式错误（字段类型错） | P2 | ❌ 自动不修复 | 高 | **人工工单** |
| PII 命中 | P0 | ❌ 自动不修复；仅脱敏 + 通知安全团队 | 极高 | **人工工单** |
| 跨表一致性冲突 | P1 | ❌ 自动不修复 | 极高 | **人工工单** |

**实现栈**：`DqAutoRepairService` 仅实现 `LOW_RISK` 子类（断连重试、pipeline 带 retry 重跑）；其他场景一律 status=MANUAL。
**写操作必须 audit**：调 `IStorageAdapter.update()` / `PipelineExecutor.execute()` 前后调 `POST /api/v1/security/audit/log`。
**兜底守卫**：自动修复动作执行前必须再次校验 `rule.severity ∈ {CRITICAL, HIGH, MEDIUM}` 是否在「允许自动修复白名单」内，不在白名单则强制 `handling_mode=MANUAL`。

### 5.6 工单中心页面

- `WorkOrderList` — 状态筛选 + 优先级徽章 + SLO 倒计时
- `WorkOrderDetail` — 详情 + 时间线 + 自动修复日志
- `WorkOrderDashboard` — KPI（P0-P3 分组 + 平均解决 + 升级率）

### 5.7 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 6 工作日 |
| 前端开发 | 1 人 × 4 工作日 |
| 安全集成 | 0.5 天 |
| 测试 | 2 天 |

### 5.8 预期效果

- P0 告警 5 分钟触达值班
- 升级机制防「漏看」
- 低级告警可自动闭环

---

## §6. 模块 5：报告与可视化

### 6.1 4 类报告

| 报告 | 周期 | 内容 |
|:--|:--|:--|
| 日报 | 每日 09:00 | 当日规则执行 + F 级 Top10 + 新增告警 + Open 工单 |
| 周报 | 每周一 09:00 | 7 日趋势 + 雷达图变化 + 新增 F 级 + 关闭率 |
| 月报 | 每月第 1 个 09:00 | 30 日趋势 + 域级健康度变化 + 规则生命周期统计 |
| 季报 | 季度首月 1 日 | 90 日趋势 + 长期趋势 + 闭环统计 + 同比/环比 |

### 6.2 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_report (
    id              VARCHAR(36) PRIMARY KEY,
    report_type     VARCHAR(16) NOT NULL,    -- DAILY/WEEKLY/MONTHLY/QUARTERLY
    report_period   VARCHAR(32) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    summary         TEXT,
    metrics         JSONB NOT NULL,
    detail          JSONB,
    dimension_trend JSONB,
    top_issues      JSONB,
    html_url        VARCHAR(191),
    pdf_url         VARCHAR(191),
    digest          TEXT,
    generated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (report_type, report_period)
);
CREATE INDEX idx_dq_report_type ON ecos_dq.dq_report(report_type, report_period DESC);
```

报告归档：HTML/PDF 上传 MinIO `ecos/dq-reports/{year}/{period}/`

### 6.3 报告生成流程（双出口）

> **决策**：报告采用「Thymeleaf 后端模板 + 前端 SPA 仪表盘」双出口（§12 #3）。
> Thymeleaf 出口用于邮件/PDF/HTML 订阅，前端 SPA 出口用于交互式探索。

#### 6.3.1 后端 Thymeleaf 报告（文件出口）

```
runtime-task 调度（DAILY/WEEKLY/MONTHLY/QUARTERLY cron）
  → DqReportService.generate(reportType, period)
    → 聚合 dq_rule_check + dq_alert_record + dq_score_snapshot + dq_work_order
    → 渲染 Thymeleaf HTML 模板（data-engine-impl/src/main/resources/templates/dq/{type}.html）
    → 转 PDF（iText/Flying Saucer 任选，不强约束）
    → 上传 MinIO（runtime-access 封装）
    → LLM 摘要（llm-gateway LLMGatewayService，失败不阻塞主流程）
    → 落 dq_report 表（html_url + pdf_url + digest）
    → 触发 IMonitorWarnContactService 推送订阅人（runtime 内部统一推送：邮件/IM）
```

模板列表（4 个，Thymeleaf fragment 复用）：
- `dq/daily.html` — 4 区块：今日执行 / F 级 Top10 / 新增告警 / Open 工单
- `dq/weekly.html` — 7 日趋势 + 雷达图（SVG 内联）+ 关闭率
- `dq/monthly.html` — 30 日趋势 + 域级健康度变化 + 规则生命周期统计（apply/obsolete）
- `dq/quarterly.html` — 90 日趋势 + 闭环率 + 同比/环比

**订阅**：`ecos_dq.dq_report_subscription`（按 domain / asset / 整体可订阅；subscription_type: DAILY/WEEKLY/MONTHLY/QUARTERLY）

#### 6.3.2 前端 SPA 仪表盘（交互出口）

```
前端 Dashboard 页面（/dq_dashboard 注册到 main.tsx）
  → 并发调用 fetchDqAll() = [rules, issues, dashboard] + fetchDqScoreSystem()
  → 局部按需：fetchDqScoresTrend({days}) / fetchDqRiskRanking() / fetchDqAlertTimeline({hours})
  → recharts 渲染：RadarChart / LineChart / Timeline
  → @10s 轮询最新告警（或 EventSource 推送，预留）
  → 报告区：GET /api/v1/dq/reports?limit=5 → 列表 + 下载按钮（MinIO URL）
```

**双出口一致性约束**：
- Thymeleaf 出口与 SPA 出口必须共享同一聚合查询层（`DqReportAggregator`）
- 任一指标（如「今日通过率」）两个出口同源，禁止各自查 `dq_rule_check` 独立计算
- 后端 `dq_report.digest` 字段只供 SPA 出口消费，Thymeleaf 直读结构化数据

### 6.4 仪表盘视觉

```
┌──────────────────────────────────────────────────────────┐
│  ① Hero：系统总体健康度 + A/B/C/D/F 徽章               │
│     四张 KPI 卡：今日告警 / Open 工单 / F 资产 / 通过率  │
├──────────────────────────────────────────────────────────┤
│  ② 维度雷达图（6 维）+ 趋势折线图（30 日）              │
├──────────────────────────────────────────────────────────┤
│  ③ 风险资产排行 Top 10（带跳转详情）                     │
├──────────────────────────────────────────────────────────┤
│  ④ 告警时间轴（24h，按 P0-P3 分组）                     │
├──────────────────────────────────────────────────────────┤
│  ⑤ 报告区（日/周/月/季切换 Tab，最近 5 份可下载）        │
└──────────────────────────────────────────────────────────┘
```

技术要点：
- 主题 `useTheme().styles`
- i18n `useLanguage().t('dqDashboard.*')`
- 图表：recharts（前端已在用）

### 6.5 数据资产详情页 DQ 子 Tab

复用 `db.*` i18n key，在资产详情加「数据质量」子 Tab，显示：
- 关联规则数 + 当前健康度
- 6 维雷达图
- 最近 7 次执行
- 告警列表

### 6.6 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 5 工作日 |
| 前端开发 | 1 人 × 4 工作日 |
| 测试 | 1 天 |

### 6.7 预期效果

- 4 类报告自动化
- 仪表盘首页即治理全局视图

---

## §7. 模块 6：改进闭环

### 7.1 闭环 5 阶段

```
① 发现问题 → ② 分析归因 → ③ 整改措施 → ④ 验证修复 → ⑤ 预防措施
   (告警)        (cognitive)   (工单)        (验证评估)   (规则强化/管道修复)
```

### 7.2 根因分析（RCA）

**集成 cognitive-engine**（铁律 2.1 只调 API）：

> **决策**（§12 #4）：P0/P1 工单进入 IN_WORK 时**自动**触发 RCA；P2/P3 工单**手动**触发（详情页按钮）。
> 自动触发：`DqRcaTrigger.autoTrigger(workOrderId)` 由 IN_WORK 状态变更监听器（`@TransactionalEventListener`）异步调度，3 次重试后写 `rca_confidence=0` 不阻塞工单。

**触发策略**：

| 工单优先级 | 触发模式 | 实现 |
|:--|:--|:--|
| P0 / P1 | 自动 | IN_WORK 状态监听器自动调 cognitive |
| P2 / P3 | 手动 | 工单详情页「运行 RCA」按钮 → `POST /api/v1/dq/work-orders/{id}/run-rca`，同语义复用 |

**调用契约**：
- 工单 IN_WORK 时，调 `POST http://localhost:18089/api/v1/cognitive/diagnose`
- 入参：failed_rule, asset_id, time_window, related_pipeline_ids
- 返回：因果链 + 候选根因（top3 概率）
- 落 `dq_work_order.rca_result JSONB`

### 7.3 预防措施

工单 resolved → 自动建议：
- 规则强化（ruleId + 新版本）
- 管道修复（pipelineId + nodeId + change）
- 数据契约更新（assetId + field NOT NULL）

`preventive_actions JSONB`：
```json
[
  {"type": "RULE_TIGHTEN", "ruleId": "dq-xxx", "change": "增加 NOT_NULL 约束", "createdVersion": 2},
  {"type": "PIPELINE_FIX", "pipelineId": "p-xxx", "nodeId": "n-2", "change": "增加 notNull 校验"},
  {"type": "DATA_CONTRACT_UPDATE", "assetId": "t-xxx", "change": "字段级 NOT NULL"}
]
```

### 7.4 闭环统计

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_improvement_cycle (
    id              VARCHAR(36) PRIMARY KEY,
    work_order_id   VARCHAR(36) NOT NULL,
    detected_at     TIMESTAMP,
    rca_completed_at TIMESTAMP,
    action_planned_at TIMESTAMP,
    action_executed_at TIMESTAMP,
    verified_at     TIMESTAMP,
    closed_at       TIMESTAMP,
    detected_to_rca_ms   BIGINT,
    rca_to_action_ms     BIGINT,
    action_to_verify_ms  BIGINT,
    verify_to_close_ms   BIGINT,
    total_ms              BIGINT,
    has_rca         BOOLEAN NOT NULL DEFAULT FALSE,
    has_preventive  BOOLEAN NOT NULL DEFAULT FALSE,
    preventive_created JSONB
);
CREATE INDEX idx_ic_wo ON ecos_dq.dq_improvement_cycle(work_order_id);
```

KPI：
- 闭环率 = (resolved & 有 preventive) / 总工单
- 平均闭环时长 = AVG(verify_to_close_ms)
- 重复工单率 = 同 root_cause 次数 / 工单数

### 7.5 验证修复

```
DqWorkOrderService.verified(workOrderId)
  → 触发 rule_id 重新执行 DqRuleExecutor
  → pass_rate 100% → VERIFIED
  → 不通过 → 回 IN_WORK，retry_count++
  → retry_count >= 3 → 强制升级人工
```

### 7.6 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 5 工作日 |
| 前端开发 | 1 人 × 3 工作日 |
| 测试 | 1 天 |

### 7.7 预期效果

- 每个 P0/P1 工单必有 RCA
- 闭环率 > 70%
- 重复工单率 < 10%

---

## §8. 模块 7：知识库

### 8.1 三大知识库

| 知识库 | 来源 | 用途 |
|:--|:--|:--|
| **规则模板库** | 内置 5 模板 + 用户贡献 | 规则创建向导 |
| **问题案例库** | 每次 resolved 工单自动归档 | 相似问题参考 |
| **最佳实践库** | 用户/外部输入 | 跨项目复用 |

### 8.2 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_dq.dq_rule_template (
    id            VARCHAR(64) PRIMARY KEY,
    template_name VARCHAR(191) NOT NULL,
    category      VARCHAR(32) NOT NULL,
    rule_type     VARCHAR(32) NOT NULL,
    default_params JSONB NOT NULL,
    description   TEXT,
    usage_count   INT NOT NULL DEFAULT 0,
    is_builtin    BOOLEAN NOT NULL DEFAULT TRUE,
    source_url    VARCHAR(191),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ecos_dq.dq_issue_case (
    id            VARCHAR(36) PRIMARY KEY,
    case_no       VARCHAR(64) UNIQUE,
    work_order_id VARCHAR(36) NOT NULL,
    rule_id       VARCHAR(64),
    asset_id      VARCHAR(64),
    symptom       TEXT,
    root_cause    TEXT,
    solution      TEXT,
    preventive    TEXT,
    tags          JSONB,
    related_knowledge TEXT,
    is_public     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_case_asset ON ecos_dq.dq_issue_case(asset_id) WHERE is_public;
CREATE INDEX idx_case_rule ON ecos_dq.dq_issue_case(rule_id) WHERE is_public;

CREATE TABLE IF NOT EXISTS ecos_dq.dq_best_practice (
    id            VARCHAR(36) PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    category      VARCHAR(32) NOT NULL,
    domain        VARCHAR(128),
    content       TEXT NOT NULL,
    related_rules JSONB,
    related_cases JSONB,
    author        VARCHAR(128),
    version       INT NOT NULL DEFAULT 1,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    is_deleted    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_bp_status ON ecos_dq.dq_best_practice(status) WHERE is_deleted = FALSE;
```

### 8.3 知识搜索

集成 kb-engine RAG（kb-engine 已有 `GET /api/v1/kb/rag/search`）：
- 用户建工单时自动调 `POST /api/v1/kb/rag/search` 找相似案例
- 入参：symptom + rule_type + domain
- 返回：top5 相似案例 + 关联最佳实践

**注意**：不直接 import kb-engine-impl（铁律 2.1），走 REST

### 8.4 API

```
GET  /api/v1/dq/templates
POST /api/v1/dq/templates
GET  /api/v1/dq/cases?assetId=&ruleId=&tags=
POST /api/v1/dq/cases
POST /api/v1/dq/cases/search
GET  /api/v1/dq/best-practices
POST /api/v1/dq/best-practices
```

### 8.5 资源需求

| 项 | 量 |
|:--|:--|
| 后端开发 | 1 人 × 4 工作日 |
| 前端开发 | 1 人 × 3 工作日 |
| 测试 | 1 天 |

### 8.6 预期效果

- 规则创建时间 -50%
- 相似问题命中率 > 80%
- 最佳实践跨项目复用

---

## §9. 模块 8：系统集成

### 9.1 集成点矩阵

| 模块 | 集成方式 | 具体 |
|:--|:--|:--|
| **data-engine**（自身） | 内部协同 | DQ 评估复用 ConnectorFactory + IStorageAdapter |
| **pipeline** | 事件驱动 | `PIPELINE_EXECUTION_SUCCEEDED` → DqRuleExecutor |
| **datasource** | 复用 | `td_datasource.connectionConfig` → 拉样本 |
| **数据资产 (CatalogTree)** | 跳转 | CatalogContextMenu 已有「配置 DQ 规则」入口（行 123）→ 跳 `/dq_dashboard?table=xxx` |
| **kb-engine** | REST | `GET /api/v1/kb/rules` 同步合规规则 |
| **kb-engine RAG** | REST | 相似问题搜索 |
| **cognitive-engine** | REST | RCA 因果链查询（P0/P1 自动触发，P2/P3 手动触发） |
| **security-engine** | REST | mask + audit/log |
| **runtime-task** | 服务调用 | DqScheduler 周期调度（已在用） |
| **runtime-core alert** | 服务调用 | `IAlertService.triggerAlert(payload, channels)` 统一推送入口；**Kafka 分发由 runtime-core 内部统一实现**（topic `ecos.dq.alert`，P0/P1/P2 触发）；**DQ 侧不自建 webhook / 客户端 / Kafka producer** |
| **runtime-monitor** | 已打通 | `countOpenDqIssues()` 暴露 `open_dq_issues` |
| **llm-gateway** | 服务调用 | 报告摘要 / RCA 摘要 |
| **MinIO** | 通过 runtime-access | 报告 PDF/HTML 归档 |
| **PG (sys_man)** | 通过 data-engine | DQ 主存储（`ecos_dq` schema） |
| **前端 SPA** | 路由 | `/dq_dashboard` 必须注册到 [main.tsx](file:///D:/workspace/javaprojects/ECOS/ecos_frontend/src/main.tsx)；仪表盘数据走 `api.ts` 中已就绪的 9 个 DQ 函数 |
| **前端 Topbar** | 路由 | Topbar 现有 DQ 菜单入口 → 复用 `/dq_dashboard` |

> **runtime 边界说明**（决策 #6/#7）：
> - 推送渠道（飞书/企微/短信/钉钉）由 runtime-core 统一实现，DQ 只调 `IAlertService.triggerAlert`，不直连 IM API
> - Kafka 接入由 runtime-core 统一实现，topic `ecos.dq.alert` 由 runtime 维护；下游 BI/监控 / 大屏 / 外部消费方通过 runtime 订阅
> - DQ 侧严禁自建任何推送 / Kafka producer，违反者验收不通过（铁律 2.5 #4「补强而非自建」）

### 9.2 三滤波器接入（铁律 1.2）

每新增 DQ Controller 必须更新：
1. `VersionPrefixRewriteFilter.java` — `/api/v1/dq/**` REMOVE 重写
2. `SecurityConfig.java` — permitAll `/api/v1/dq/**` + `/api/dq/**`（双路径）
3. `ClearanceInterceptor.java` — 豁免列表加同两种
4. `auth.whitelist.paths` (application.yml) 注册

### 9.3 序列图（Pipeline 完成后自动 DQ 检查）

```
Pipeline 执行实例
    │  PipelineExecutionSucceededEvent
    ↓
PipelineExecutionListener (data-engine)
    │  query: SELECT id FROM dq_rule WHERE target_pipeline_id = ?
    │  AND status='ACTIVE' AND target_kind='PIPELINE'
    ↓
DqScheduler.runRuleBatch(scheduleId=auto)
    │  对每条 rule：td_datasource → IStorageAdapter → 评估
    ↓
dq_rule_check 写入
    │  pass=false
    ↓
DqAlertDispatcher.dispatch()
    │  ① IAlertService.triggerAlert (runtime-core)
    │  ② INSERT dq_alert_record
    │  ③ 通知推送（mask → 关 → webhook）
    ↓
告警记录
    │  创建工单
    ↓
DqWorkOrderService.create(workOrder)
    │  P0/P1 → 分配值班；P2/P3 → AI 自动修复尝试
    ↓
DqAutoRepairService.tryRepair(workOrder)  (for P2/P3)
    │  触发 RCA (调 cognitive-engine)
    ↓
workOrder.rca_result
    │  验证修复（重跑 DqRuleExecutor）
    ↓
verdict → VERIFIED / 回 IN_WORK
```

### 9.4 实施顺序（依赖）

```
Phase 1: 基础设施 (W1-W2)
  ├─ T1: V110 Flyway 迁移 (5 张新表)
  ├─ T2: DQ 三滤波器接入
  └─ T3: DqControllerV2 替换旧版（保留兼容层）

Phase 2: 规则与评估 (W2-W3)
  ├─ T4: 规则状态机 + 版本快照
  ├─ T5: 规则类别/域分类
  ├─ T6: 评分引擎（6 维度）
  └─ T7: 前端规则中心 + RuleTemplateLibrary 挂路由

Phase 3: 监控与告警 (W3-W4)
  ├─ T8: 监控调度器 + cron + 事件触发
  ├─ T9: DqAlertDispatcher + 告警记录落库
  ├─ T10: 工单状态机 + 自动修复
  └─ T11: 三滤波器 + 安全集成（mask + audit）

Phase 4: 高级能力 (W4-W5)
  ├─ T12: RCA 集成 cognitive-engine
  ├─ T13: 4 类报告生成 + 仪表盘
  ├─ T14: 知识库 3 库 + RAG 集成
  └─ T15: 闭环 KPI 看板

Phase 5: 验收 (W5)
  ├─ T16: 浏览器 E2E
  ├─ T17: 安全审计（mask + audit 验证）
  └─ T18: 文档（AGENTS.md + PMO-48 实施报告）
```

---

## §10. 实施步骤总表

| Phase | 周期 | 工作 | 准入 |
|:--|:--|:--|:--|
| 1 基础设施 | W1-W2 | 迁移 + 兼容层 + 三滤波器 | 后端架构 + DB |
| 2 规则与评估 | W2-W3 | 状态机 + 评分 | Phase 1 完成 |
| 3 监控与告警 | W3-W4 | 调度 + 告警 + 工单 | Phase 2 完成 |
| 4 高级能力 | W4-W5 | RCA + 报告 + 知识库 + 闭环 | Phase 3 完成 |
| 5 验收 | W5 | E2E + 安全审计 + 文档 | Phase 4 完成 |

**里程碑**：
- M1（W2 末）：规则中心生命周期完整
- M2（W3 末）：监控告警全链路打通
- M3（W4 末）：仪表盘 + 知识库可演示
- M4（W5 末）：完整 DQ Dashboard 上线

---

## §11. 资源与风险

### 11.1 资源需求汇总

| 类型 | 数量 | 周期 |
|:--|:--|:--|
| 后端开发 | 1.5 人 × 5 周 | 75 工作日 |
| 前端开发 | 1.5 人 × 5 周 | 75 工作日 |
| QA | 1 人 × 2 周 | 10 工作日 |
| 安全审计 | 0.5 人 × 1 周 | 3 工作日 |
| 总计 | — | 163 人日 |

### 11.2 风险清单

| 风险 | 概率 | 影响 | 缓解 |
|:--|:--|:--|:--|
| 双轨 DQ 系统切换 | 高 | 高 | Phase 1 保留旧端点只读兼容；Phase 5 后下线 |
| 告警落 PG 高频写 | 中 | 中 | 异步 dispatcher；P3 仅落库 |
| cognitive RCA 不可用 | 中 | 中 | 降级：失败返「无法归因」不阻塞 |
| 大表评估慢 | 中 | 中 | sample_size ≤ 10000；超时 60s 自动取消 |
| 前端大文件违反规范 | 中 | 低 | 每个 Tab 独立文件，主布局 < 300 行 |
| 三滤波器漏更新 | 高 | 高 | PMO 任务卡硬卡：每 Controller 必列修改清单 |
| rule_version 表膨胀 | 低 | 低 | 100 版后归档 |
| LLM 摘要不可用 | 中 | 低 | 异步失败不阻塞 |

### 11.3 验收门禁

- 后端：`mvn install -DskipTests` 通过（铁律 1.5）
- 前端：tsc + lint 通过（铁律 5.1 #11）
- 浏览器 E2E：渲染无 ErrorBoundary + console 无 error + network 无 4xx/5xx
- curl 验收：每新增端点必配套
- 安全卡：grep mask/audit 命中 ≥ 1（铁律 2.4 #7）
- ArchUnit：DQ 模块不 import kb-engine-impl / cognitive-engine-impl

### 11.4 预期效果（量化）

| 指标 | 当前 | 目标 |
|:--|:--|:--|
| 规则数量 | ~10 | ≥ 200 |
| 规则分类覆盖率 | 0% | 100% |
| 规则生命周期完整率 | ~10% | 100% |
| 周期监控抽检点 | 1 | 5+ |
| 实时检测延迟 | 无 | < 5 min |
| 告警触达时延 | 无通道 | P0: 5 min |
| 工单闭环率 | 无 | ≥ 70% |
| F 级资产比例 | 不可测 | ≤ 10% |
| 域级健康度 | 不可测 | 100% |
| 报告自动化 | 无 | 100% |

---

## §12. 决策项总览（已决策）

> ☑ 以下 7 项均经决策确认（2026-09-10）。后续如需变更，走 PMO 变更评审流程。

| # | 决策项 | 决策结论 | 落地点（章节） |
|:--|:--|:--|:--|
| 1 | 规则表迁移策略 | **完全合并**：`ecos_dq_rule_v2` + `ecos_quality_rule` 双轨合并到 `ecos_dq.dq_rule`。旧端点保留只读兼容 2 个迭代后下线（新代码已切到 `dq_rule`），Phase 5 后下线旧端点。合并期间禁止向旧表写入 | §2.2 / §9.4 |
| 2 | 告警落 PG 等级 | **P0-P3 全量落库**（审计溯源 + 默认 DENY 内网下必须可回溯）；**仅 P0/P1/P2 推送**，P3 仅落库不通知（邮件日报消化） | §5.2 / §5.3 |
| 3 | 报告渲染方式 | **Thymeleaf + 前端 SPA 双出口**。Thymeleaf 出口用于邮件/PDF/HTML 订阅（4 模板：daily/weekly/monthly/quarterly.html），SPA 出口用于交互式探索。两出口共享 `DqReportAggregator` 聚合查询层 | §6.3 |
| 4 | RCA 触发时机 | **P0/P1 自动触发**（IN_WORK 监听器 → `@TransactionalEventListener` 异步调 cognitive，3 次重试后写 `rca_confidence=0`）；**P2/P3 手动**（详情页「运行 RCA」按钮） | §7.2 |
| 5 | 自动修复范围 | **仅低危场景自动**：① 数据源瞬时断连 3 次重试；② Pipeline 节点 retry 已配置。其余（空值/格式/PII/一致性）一律人工工单。兜底白名单 + severity 校验 | §5.5 |
| 6 | 推送渠道 | **由 runtime 统一实现**。DQ 只调 `IAlertService.triggerAlert(payload, channels)`，不直连 IM API。runtime 维护飞书/企微/短信/钉钉 channel | §5.3 / §9.1 |
| 7 | Kafka 接入 | **由 runtime 统一实现**。topic `ecos.dq.alert` 由 runtime-core 维护，P0/P1/P2 触发后由 runtime publish。下游（监控/大屏/外部）通过 runtime 订阅。DQ 侧不操作 Kafka | §5.3 / §9.1 |

**联动影响**（决策 #6/#7 带来的工程变更）：
- `DqAlertDispatcher` 不实现 IM 推送客户端，只调 `IAlertService.triggerAlert`
- `DqAlertDispatcher` 不操作 Kafka，仅声明 `notifyChannels` 偏好供 runtime 解释
- §9.4 Phase 3 (T9) 工作项「DqAlertDispatcher + 告警记录落库」中已包含 P0/P1/P2 推送路径与 P3 仅落库路径

---

## §13. 执行建议

- 批准本方案后，按 §9.4 进入 Phase 1（基础设施）
- 每个 Phase 末尾产出验收记录（V1-V4 四步法）
- PMO-48 后续按 Phase 拆分为 PMO-48-A/B/C/D 子指令并行/串行推进
- 安全审计项（铁律 2.4 #7）作为 Phase 5 必过门禁
- 进入 Phase 1 前先通过架构评审 + 安全评审两次卡点（铁律 1.2 #1 ~ #5 + 2.4 #7）
- 建议 PMO-48-A 子指令：T1 = DB 迁移 / T2 = 三滤波器接入 / T3 = DqControllerV2 替换（保留旧端点只读兼容）

---

**方案文档版本**：v1.1-decided（已按 §12 7 项决策修订）
**下次评审节点**：方案按 Phase 1 启动前做 1 次架构评审 + 1 次安全评审，通过即可拆 PMO-48-A 子指令
