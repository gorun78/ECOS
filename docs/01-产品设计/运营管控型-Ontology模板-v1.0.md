# 运营管控型 Ontology 模板 v1.0

> 标杆客户：科创信息（项目型企业 / 运营管控型）
> 目标：CEO登录后5分钟内看到公司健康度全貌
> 适配范围：任何项目型、总部强管控的企业，改配置不写代码

---

## 一、Domain 定义

```sql
-- 运营管控型业务域
INSERT INTO ecos_ontology_domain (id, code, name, description)
VALUES ('dom_opsctrl', 'OPS_CTRL', '运营管控域', '项目型企业的运营管控核心实体与关系');
```

## 二、实体定义（7实体 = CEO最小闭环）

### 实体总览

| # | Code | 中文名 | 类型 | 说明 | 已有表对齐 |
|---|------|--------|------|------|-----------|
| 1 | Department | 事业部 | MASTER | 公司事业单元 | ecos_biz_department |
| 2 | Project | 项目 | MASTER | 交付/研发/管理项目 | ecos_biz_project |
| 3 | Contract | 合同 | TRANSACTION | 收入/支出合同 | ecos_biz_contract |
| 4 | Metric | 经营指标 | METRIC | 月度经营数据 | ecos_biz_metric |
| 5 | Target | 年度目标 | METRIC | 公司/部门年度目标 | ecos_biz_target |
| 6 | Goal | 战略目标 | STRATEGIC | 可分解的战略目标节点 | ecos_wm_goal |
| 7 | CausalLink | 因果链 | RELATION | 目标间因果关系 | ecos_wm_causal_link |

### 2.1 Department（事业部）

```
属性: name, manager, annual_revenue_target, annual_profit_target
主键: id (如 jd/sz/ex/cw/zh)
数据源: ecos_biz_department
```

| 属性 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 编码 | code | VARCHAR | 是 | jd/sz/ex/cw/zh |
| 名称 | name | VARCHAR | 是 | 机电事业部 |
| 负责人 | manager | VARCHAR | 是 | 张伟 |
| 年度营收目标 | annual_revenue_target | NUMERIC | 否 | 分解自公司目标 |
| 年度利润目标 | annual_profit_target | NUMERIC | 否 | 分解自公司目标 |

### 2.2 Project（项目）

```
属性: name, project_type, status, contract_amount, progress, start_date, end_date
主键: id
外键: dept_id → Department.id, goal_id → Goal.id
数据源: ecos_biz_project
```

| 属性 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 项目名称 | name | VARCHAR | 是 | 湖南省政务云平台建设 |
| 项目类型 | project_type | VARCHAR | 是 | production/research/management |
| 状态 | status | VARCHAR | 是 | planning/in_progress/completed/paused |
| 合同金额 | contract_amount | NUMERIC | 否 | 万元 |
| 进度(%) | progress | NUMERIC | 否 | 0-100，月度更新 |
| 客户名称 | customer_name | VARCHAR | 否 | |
| 开始日期 | start_date | DATE | 否 | |
| 结束日期 | end_date | DATE | 否 | |
| 所属部门 | dept_id | VARCHAR | 是 | FK→Department |
| 关联目标 | goal_id | BIGINT | 否 | FK→Goal（如"项目交付准时率"） |

### 2.3 Contract（合同）

```
属性: contract_no, contract_type, amount, status, signed_date
主键: id
外键: project_id → Project.id
数据源: ecos_biz_contract
```

| 属性 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 合同编号 | contract_no | VARCHAR | 是 | |
| 合同类型 | contract_type | VARCHAR | 是 | income/expense |
| 金额 | amount | NUMERIC | 是 | 万元 |
| 签约方 | party_name | VARCHAR | 否 | |
| 签订日期 | signed_date | DATE | 否 | |
| 状态 | status | VARCHAR | 是 | active/completed/terminated |
| 所属项目 | project_id | VARCHAR | 是 | FK→Project |

### 2.4 Metric（经营指标）

```
属性: metric_type, metric_value, metric_month
主键: id
外键: dept_id → Department.id, goal_id → Goal.id
数据源: ecos_biz_metric
```

| 属性 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 指标类型 | metric_type | VARCHAR | 是 | revenue/profit/collection/cost |
| 指标值 | metric_value | NUMERIC | 是 | 当月实际值 |
| 目标值 | target_value | NUMERIC | 否 | 当月目标值（用于偏差计算） |
| 月份 | metric_month | VARCHAR | 是 | 2024-06 |
| 所属部门 | dept_id | VARCHAR | 是 | FK→Department |
| 关联目标 | goal_id | BIGINT | 否 | FK→Goal |

**metric_type 枚举（运营管控型核心4指标）**：

| 值 | 中文 | 计算方式 | CEO关注度 |
|----|------|----------|-----------|
| revenue | 营收 | SUM(当月收入合同金额) | ⭐⭐⭐ |
| profit | 利润 | 营收 - 成本 | ⭐⭐⭐ |
| collection | 回款 | SUM(当月实际回款金额) | ⭐⭐ |
| cost | 成本 | SUM(当月支出+人力成本) | ⭐ |

### 2.5 Target（年度目标）

```
属性: target_type, target_value, target_year
主键: id
外键: dept_id → Department.id
数据源: ecos_biz_target
```

| 属性 | 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 目标类型 | target_type | VARCHAR | 是 | 对齐 metric_type |
| 目标值 | target_value | NUMERIC | 是 | 年度总额 |
| 目标年度 | target_year | INTEGER | 是 | 2026 |
| 所属部门 | dept_id | VARCHAR | 是 | FK→Department |

### 2.6 Goal（战略目标节点）

```
主键: id (自增BIGINT)
数据源: ecos_wm_goal
```

| 字段 | 类型 | 说明 |
|------|------|------|
| name | VARCHAR | 目标名称（如"年度营收增长20%"）|
| parent_id | BIGINT | 父目标ID（形成树形分解）|
| target_value | NUMERIC | 目标值 |
| current_value | NUMERIC | 当前值（自动从Metric汇总）|
| unit | VARCHAR | 单位（%/万元/分）|
| goal_type | VARCHAR | STRATEGIC/OKR/KPI |
| progress | INTEGER | 0-100，自动计算 |
| status | VARCHAR | active/at_risk/critical/achieved |
| kpi_formula | VARCHAR | 自动计算公式（如 link:ecos_biz_metric:revenue:SUM）|
| measure_frequency | VARCHAR | monthly/quarterly/yearly |
| alert_threshold_warn | NUMERIC | 黄色预警阈值（偏差%）|
| alert_threshold_critical | NUMERIC | 红色预警阈值（偏差%）|
| weight | INTEGER | 同层级权重（用于综合评分）|
| org_id | VARCHAR | 负责部门 FK→Department |
| owner_user_id | VARCHAR | 负责人 |

### 2.7 CausalLink（因果链）

```
数据源: ecos_wm_causal_link
```

| 字段 | 类型 | 说明 |
|------|------|------|
| source_goal_id | BIGINT | 因（驱动力来源） |
| target_goal_id | BIGINT | 果（被影响的目标） |
| relationship_type | VARCHAR | drives/enables/supports/constrains |
| description | TEXT | 因果说明 |
| time_lag_days | INTEGER | 因果滞后天数（如项目进度下降后30天才反映到回款）|
| correlation_coefficient | NUMERIC | 关联系数 0-1，用于影响力度量 |

---

## 三、关系定义（实体间）

```sql
-- 运营管控型核心关系
-- 关系表: ecos_ontology_relationship
```

| # | Code | 源实体 | 目标实体 | 类型 | 说明 |
|---|------|--------|----------|------|------|
| R1 | OWNS | Department | Project | ONE_TO_MANY | 事业部拥有多个项目 |
| R2 | HAS_CONTRACT | Project | Contract | ONE_TO_MANY | 项目关联多个合同 |
| R3 | REPORTS | Department | Metric | ONE_TO_MANY | 部门产生多期指标 |
| R4 | DECOMPOSES | Goal(parent) | Goal(child) | ONE_TO_MANY | 公司目标分解为部门子目标 |
| R5 | MEASURES | Goal | Metric | ONE_TO_MANY | 目标通过指标来衡量 |
| R6 | TARGETS | Target | Department | MANY_TO_ONE | 年度目标分配到部门 |

---

## 四、目标树（Goal Tree）

科创信息运营管控型目标分解（3层14节点）：

```
L1 公司战略目标 (goal_type=STRATEGIC)
├─ G1 年度营收增长20%         [weight:40]  kpi: revenue SUM
│  ├─ G1.1 机电事业部营收8亿  [weight:40]  kpi: revenue dept=jd SUM
│  ├─ G1.2 数字信息事业部营收12亿 [weight:40] kpi: revenue dept=sz SUM
│  └─ G1.3 E行事业部营收5.2亿  [weight:20]  kpi: revenue dept=ex SUM
│
├─ G2 年度利润率≥15%          [weight:30]  kpi: profit/revenue
│  ├─ G2.1 项目毛利率≥25%     [weight:50]  kpi: (revenue-cost)/revenue per project
│  └─ G2.2 管理费用占比≤6%    [weight:50]  kpi: management_cost/total_revenue
│
├─ G3 年度回款率≥85%          [weight:20]  kpi: collected/billed
│  ├─ G3.1 机电回款率≥85%     [weight:40]
│  ├─ G3.2 数字信息回款率≥85% [weight:40]
│  └─ G3.3 E行回款率≥85%      [weight:20]
│
└─ G4 项目交付准时率≥90%      [weight:10]  kpi: on_time_projects/total
   ├─ G4.1 生产型项目准时率≥90%  [weight:60]
   └─ G4.2 研发型项目准时率≥80%  [weight:40]
```

**Goal数据示例**：

| id | name | parent | goal_type | target | unit | kpi_formula |
|----|------|--------|-----------|--------|------|-------------|
| 1 | 年度营收增长20% | NULL | STRATEGIC | 20 | % | link:metric:revenue:SUM:yoy |
| 11 | 机电事业部营收8亿 | 1 | OKR | 80000 | 万元 | link:metric:revenue:SUM:dept=jd |
| 12 | 数字信息事业部营收12亿 | 1 | OKR | 120000 | 万元 | link:metric:revenue:SUM:dept=sz |
| 13 | E行事业部营收5.2亿 | 1 | OKR | 52000 | 万元 | link:metric:revenue:SUM:dept=ex |
| 2 | 年度利润率≥15% | NULL | STRATEGIC | 15 | % | formula:profit/revenue |
| 3 | 年度回款率≥85% | NULL | STRATEGIC | 85 | % | link:metric:collection:AVG |
| 4 | 项目交付准时率≥90% | NULL | STRATEGIC | 90 | % | link:project:progress:on_time_ratio |

---

## 五、因果链（Causal Links）

运营管控型核心因果链（8条）：

```
L1 战略层因果
  CL1: 项目交付准时率↓ ──drives──→ 客户满意度↓    [coeff:0.7, lag:0]
  CL2: 客户满意度↓    ──drives──→ 年度营收↓        [coeff:0.6, lag:90]
  CL3: 成本超支↑      ──constrains──→ 利润率↓      [coeff:0.9, lag:30]

L2 运营层因果
  CL4: 项目进度↓      ──drives──→ 回款率↓          [coeff:0.8, lag:60]
  CL5: 回款率↓        ──drives──→ 现金流压力↑      [coeff:0.85, lag:30]
  CL6: AI采纳率↓      ──enables──→ 交付效率↓       [coeff:0.5, lag:180]

L3 组织层因果
  CL7: 研发投入↓      ──enables──→ AI采纳率↓       [coeff:0.6, lag:365]
  CL8: 交付效率↓      ──drives──→ 项目交付准时率↓   [coeff:0.7, lag:30]
```

**因果链数据示例**：

| id | source_goal | target_goal | type | coeff | lag_days | 说明 |
|----|-------------|-------------|------|-------|----------|------|
| 10 | G4(交付准时) | G1(营收) | drives | 0.6 | 90 | 交付延迟3个月后反映到营收 |
| 11 | G4(交付准时) | G3(回款) | drives | 0.8 | 60 | 进度滞后2个月影响回款 |
| 12 | G3(回款) | G2(利润) | drives | 0.5 | 30 | 回款影响资金成本和利润 |
| 13 | G4.1(生产项目准时) | G4(交付准时) | drives | 0.85 | 0 | 生产型项目是交付主体 |

---

## 六、CEO首页数据流

```
CEO打开首页 →
  /api/v1/ecos/ceo/home →
    ├─ 查 Goal树: ecos_wm_goal WHERE goal_type IN ('STRATEGIC','OKR')
    │   → 计算 current_value (执行kpi_formula)
    │   → 计算 deviation = (current-target)/target
    │   → 状态判定: 偏差>alert_threshold_critical → CRITICAL
    │
    ├─ 查 趋势: ecos_biz_metric (最近6个月)
    │   → 按 metric_type 聚合月度趋势
    │
    ├─ 查 偏差Top3: Goal树中 deviation 最大的3个
    │   → 调用 trace_causal_chain 沿因果链追溯根因
    │
    └─ 返回一屏数据:
        {
          "goals": [目标树+偏差],
          "trends": [6个月趋势],
          "top_deviations": [偏差Top3+根因链],
          "alerts": [需关注事项]
        }
```

---

## 七、落库SQL（核心片段）

完整Migration文件独立维护。此处给出关键INSERT模板：

```sql
-- V40__ops_control_ontology_template.sql

-- 1. Domain
INSERT INTO ecos_ontology_domain (id, code, name) 
VALUES ('dom_opsctrl', 'OPS_CTRL', '运营管控域');

-- 2. Entities (7条)
INSERT INTO ecos_ontology_entity (id, code, name, entity_type, domain_id, sort_order) VALUES
('ent_ops_dept',    'Department',  '事业部',   'MASTER',      'dom_opsctrl', 1),
('ent_ops_project', 'Project',     '项目',     'MASTER',      'dom_opsctrl', 2),
('ent_ops_contract','Contract',    '合同',     'TRANSACTION', 'dom_opsctrl', 3),
('ent_ops_metric',  'Metric',      '经营指标', 'METRIC',      'dom_opsctrl', 4),
('ent_ops_target',  'Target',      '年度目标', 'METRIC',      'dom_opsctrl', 5),
('ent_ops_goal',    'Goal',        '战略目标', 'STRATEGIC',   'dom_opsctrl', 6),
('ent_ops_clink',   'CausalLink',  '因果链',   'RELATION',    'dom_opsctrl', 7);

-- 3. Relations (6条)
INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, relationship_type) VALUES
('rel_ops_owns',       'ent_ops_dept',    'ent_ops_project',  'OWNS',         'ONE_TO_MANY'),
('rel_ops_has_contract','ent_ops_project', 'ent_ops_contract', 'HAS_CONTRACT', 'ONE_TO_MANY'),
('rel_ops_reports',    'ent_ops_dept',    'ent_ops_metric',   'REPORTS',      'ONE_TO_MANY'),
('rel_ops_decomposes', 'ent_ops_goal',    'ent_ops_goal',     'DECOMPOSES',   'ONE_TO_MANY'),
('rel_ops_measures',   'ent_ops_goal',    'ent_ops_metric',   'MEASURES',     'ONE_TO_MANY'),
('rel_ops_targets',    'ent_ops_target',  'ent_ops_dept',     'TARGETS',      'MANY_TO_ONE');

-- 4. Goal Tree (L1 + L2 共14个节点) — 见第四节目标树
-- 核心公式: kpi_formula 字段定义自动计算规则
-- 例: 'link:metric:revenue:SUM' = 查ecos_biz_metric WHERE metric_type='revenue' SUM(metric_value)

-- 5. Causal Links (8条) — 见第五节因果链
-- source_goal_id/target_goal_id 指向 ecos_wm_goal.id
```
