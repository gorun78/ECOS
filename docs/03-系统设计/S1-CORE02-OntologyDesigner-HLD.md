# S1-CORE02: Ontology Designer HLD

> **版本**: v1.0  
> **日期**: 2025-06-17  
> **作者**: ECOS-ARCH  
> **来源**: 第6章 Ontology平台 功能设计文档 (1026-第6章 ontoloty平台.txt, 1175行)

---

## 1. 模块定位

Ontology Platform 是 ECOS 的**核心语义层**，负责将底层数据（Data）转化为业务对象（Entity/Object），并定义实体之间的关系（Relationship）、业务动作（Action）、业务规则（Rule）。是整个 ECOS 体系的知识骨架。

```
Data → Knowledge → Ontology → Object Runtime → Workflow → Agent → Mission
       Table → Entity → Property | Relationship | Action | Rule
```

对应 Palantir Foundry Ontology Manager / Ontology Editor。

---

## 2. 现有代码分析

### 2.1 现有控制器

#### OntologyController (`/api/v1/ecos/ontologies`)

| 方法 | 路径 | 功能 | 差距 |
|------|------|------|------|
| GET | `/{ontologyId}/entities` | 实体列表 | ✅ 完整 |
| POST | `/{ontologyId}/entities` | 创建实体 | ⚠️ 缺少字段校验、唯一性检查 |
| PUT | `/{ontologyId}/entities/{id}` | 更新实体 | ✅ 完整 |
| DELETE | `/{ontologyId}/entities/{id}` | 删除实体 | ⚠️ 缺级联影响分析 |
| GET | `/entities/{entityId}/properties` | 属性列表 | ✅ 完整 |
| POST | `/entities/{entityId}/properties` | 创建属性 | ⚠️ 缺枚举值/默认值支持 |
| PUT | `/entities/{entityId}/properties/{id}` | 更新属性 | ✅ 完整 |
| DELETE | `/entities/{entityId}/properties/{id}` | 删除属性 | ⚠️ 缺引用检查 |
| GET | `/entities/{entityId}/relationships` | 实体关系 | ✅ 完整 |
| POST | `/entities/{entityId}/relationships` | 创建关系 | ⚠️ 缺循环检测 |
| DELETE | `/entities/{entityId}/relationships/{id}` | 删除关系 | ✅ 完整 |
| GET | `/relationships` | 全部关系 | ✅ 完整 |

#### OntologyActionController (`/api/v1/ecos`)

| 方法 | 路径 | 功能 | 差距 |
|------|------|------|------|
| GET | `/entities/{entityId}/actions` | 实体动作列表 | ✅ 完整 |
| GET | `/actions` | 全部动作 | ✅ 完整 |
| POST | `/entities/{entityId}/actions` | 创建动作 | ⚠️ 缺执行策略/权限绑定 |
| PUT | `/entities/{entityId}/actions/{id}` | 更新动作 | ✅ 完整 |
| DELETE | `/entities/{entityId}/actions/{id}` | 删除动作 | ✅ 完整 |

### 2.2 已有数据库表

```
ecos_ontology_entity        — 实体 (id, ontology_id, code, name, description, entity_type, sort_order, ...)
ecos_ontology_property      — 属性 (id, entity_id, code, name, property_type, required_flag, searchable_flag, ...)
ecos_ontology_relationship  — 关系 (id, source_entity_id, target_entity_id, code, name, relationship_type, ...)
ecos_ontology_action        — 动作 (id, entity_id, name, action_type, rule_json, strategy, status, ...)
```

### 2.3 缺失模块分析

| 编号 | 设计文档模块 | 现有状态 | 优先级 |
|------|-------------|---------|--------|
| GAP-01 | **Domain Management** (6.3) | ❌ 完全缺失 | 🔴 P0 |
| GAP-02 | **Entity Designer** (6.4) | ⚠️ 基础 CRUD 存在，缺分类（Master/Transaction/Event/Reference） | 🟡 P1 |
| GAP-03 | **Property Designer** (6.5) | ⚠️ 基础 CRUD 存在，缺枚举类型/默认值/校验表达式 | 🟡 P1 |
| GAP-04 | **Relationship Designer** (6.6) | ⚠️ 基础 CRUD 存在，缺循环检测/影响分析 | 🟡 P1 |
| GAP-05 | **Action Designer** (6.7) | ⚠️ 基础 CRUD 存在，缺 Action 执行流程/权限绑定/前后置条件 | 🔴 P0 |
| GAP-06 | **Rule Designer** (6.9) | ❌ 完全缺失 — 无表、无API | 🔴 P0 |
| GAP-07 | **Ontology Versioning** (6.11) | ❌ 完全缺失 — 无版本管理 | 🔴 P0 |
| GAP-08 | **Ontology Publishing** (6.12) | ❌ 完全缺失 — 无发布流程 | 🔴 P0 |
| GAP-09 | **Ontology Governance** (6.13) | ❌ 完全缺失 — 无治理检查 | 🟢 P2 |
| GAP-10 | **Ontology Explorer** (6.14) | ❌ 完全缺失 — 无可视化浏览 | 🟡 P1 |
| GAP-11 | **Ontology Graph** (6.15) | ❌ 完全缺失 — 无关系图可视化 API | 🟡 P1 |
| GAP-12 | **Agent Ontology Assistant** (6.19) | ❌ 完全缺失 | 🟢 P2 |

---

## 3. 六大子模块 API 设计

### 3.1 Domain Management（领域管理）

```
GET    /api/v1/ecos/domains                                   — 领域列表
POST   /api/v1/ecos/domains                                   — 创建领域
GET    /api/v1/ecos/domains/{domainCode}                       — 领域详情
PUT    /api/v1/ecos/domains/{domainCode}                       — 更新领域
DELETE /api/v1/ecos/domains/{domainCode}                       — 删除领域
POST   /api/v1/ecos/domains/{domainCode}/publish               — 发布领域
POST   /api/v1/ecos/domains/{domainCode}/deprecate             — 废弃领域
```

**领域生命周期**:
```
Draft → Published → Deprecated
```

**创建请求示例**:
```json
{
  "code": "Sales",
  "name": "销售域",
  "owner": "user001",
  "description": "管理客户、合同、商机的业务域"
}
```

### 3.2 Entity Designer（实体设计器）

```
GET    /api/v1/ecos/domains/{domainCode}/entities              — 域下实体列表
POST   /api/v1/ecos/domains/{domainCode}/entities              — 创建实体
GET    /api/v1/ecos/entities/{entityId}                        — 实体详情（含属性/关系/动作/规则）
PUT    /api/v1/ecos/entities/{entityId}                        — 更新实体
DELETE /api/v1/ecos/entities/{entityId}                        — 删除实体（级联检查）
GET    /api/v1/ecos/entities/{entityId}/dependencies           — 影响分析
```

**实体分类**:
- `MASTER` — 主数据实体（Customer, Supplier, Product）
- `TRANSACTION` — 交易实体（Order, Invoice）
- `EVENT` — 事件实体（InspectionEvent, RiskEvent）
- `REFERENCE` — 引用实体（Country, Region）

**创建请求示例**:
```json
{
  "code": "Customer",
  "name": "客户",
  "entityType": "MASTER",
  "description": "企业客户主数据",
  "domainCode": "Sales"
}
```

### 3.3 Property Designer（属性设计器）

```
GET    /api/v1/ecos/entities/{entityId}/properties              — 属性列表
POST   /api/v1/ecos/entities/{entityId}/properties              — 创建属性
GET    /api/v1/ecos/entities/{entityId}/properties/{propId}     — 属性详情
PUT    /api/v1/ecos/entities/{entityId}/properties/{propId}     — 更新属性
DELETE /api/v1/ecos/entities/{entityId}/properties/{propId}     — 删除属性
```

**属性类型扩展**:
| 类型 | 说明 | 扩展字段 |
|------|------|----------|
| STRING | 字符串 | maxLength, pattern |
| NUMBER | 数值 | min, max, precision |
| BOOLEAN | 布尔 | defaultValue |
| DATE | 日期 | format |
| ENUM | 枚举 | enumValues: ["VIP","Regular","New"] |
| REFERENCE | 引用 | refEntityCode |
| JSON | 结构化 | schema |

**创建请求示例**:
```json
{
  "code": "Level",
  "name": "客户等级",
  "propertyType": "ENUM",
  "requiredFlag": 1,
  "uniqueFlag": 0,
  "searchableFlag": 1,
  "enumValues": ["VIP", "Gold", "Silver", "Regular"],
  "defaultValue": "Regular",
  "validationRule": "required"
}
```

### 3.4 Relationship Designer（关系设计器）

```
GET    /api/v1/ecos/entities/{entityId}/relationships           — 实体的关系列表
POST   /api/v1/ecos/relationships                              — 创建关系
GET    /api/v1/ecos/relationships/{relId}                       — 关系详情
PUT    /api/v1/ecos/relationships/{relId}                       — 更新关系
DELETE /api/v1/ecos/relationships/{relId}                       — 删除关系
POST   /api/v1/ecos/relationships/validate                     — 验证关系（循环检测）
GET    /api/v1/ecos/relationships/graph                        — 全局关系图谱数据
```

**关系类型**:
- `ONE_TO_ONE` — 一对一
- `ONE_TO_MANY` — 一对多
- `MANY_TO_MANY` — 多对多

**创建请求**:
```json
{
  "sourceEntityId": "ent-customer",
  "targetEntityId": "ent-order",
  "code": "PLACED",
  "name": "下达",
  "relationshipType": "ONE_TO_MANY",
  "cardinality": {"sourceMin": 0, "sourceMax": "N", "targetMin": 0, "targetMax": "N"}
}
```

### 3.5 Action Designer（动作设计器）

```
GET    /api/v1/ecos/entities/{entityId}/actions                — 实体动作列表
POST   /api/v1/ecos/entities/{entityId}/actions                — 创建动作
GET    /api/v1/ecos/actions/{actionId}                         — 动作详情
PUT    /api/v1/ecos/actions/{actionId}                         — 更新动作
DELETE /api/v1/ecos/actions/{actionId}                         — 删除动作
POST   /api/v1/ecos/actions/{actionId}/test                    — 测试动作执行
```

**Action 结构增强**:
```json
{
  "code": "Approve",
  "name": "审批通过",
  "actionType": "STATE_CHANGE",
  "targetEntityCode": "Customer",
  "description": "审批客户准入",
  "preconditions": {
    "requiredStatus": ["Submitted"],
    "requiredRole": "SalesManager",
    "validationRules": ["rule-credit-check"]
  },
  "effects": {
    "targetStatus": "Approved",
    "triggerWorkflows": ["wf-customer-onboarding"],
    "notifyRoles": ["CustomerOwner"],
    "auditRequired": true
  },
  "strategy": "SYNC"
}
```

**Action 执行流程**:
```
用户触发 Action → 权限校验 → 前置规则校验 → 执行 → 状态变更 → 事件发布 → Workflow 触发 → 通知
```

### 3.6 Rule Designer（规则设计器）

```
GET    /api/v1/ecos/entities/{entityId}/rules                  — 规则列表
POST   /api/v1/ecos/entities/{entityId}/rules                  — 创建规则
GET    /api/v1/ecos/rules/{ruleId}                             — 规则详情
PUT    /api/v1/ecos/rules/{ruleId}                             — 更新规则
DELETE /api/v1/ecos/rules/{ruleId}                             — 删除规则
POST   /api/v1/ecos/rules/{ruleId}/test                        — 测试规则
POST   /api/v1/ecos/rules/evaluate                             — 批量评估规则
```

**规则类型**:
- `VALIDATION` — 校验规则（字段格式、必填检查）
- `CALCULATION` — 计算规则（自动计算字段值）
- `DECISION` — 决策规则（条件判定）
- `AGENT` — Agent 规则（由 AI 辅助判定）

**创建请求**:
```json
{
  "code": "rule-vip-level",
  "name": "VIP客户等级判定",
  "ruleType": "DECISION",
  "entityCode": "Customer",
  "expression": "sales_amount > 10000000",
  "action": "SET(Level, 'VIP')",
  "priority": 1,
  "enabled": true,
  "description": "年销售额超过1000万的客户自动标记为VIP"
}
```

---

## 4. 版本管理状态机

### 4.1 Ontology 版本状态

```
Draft → Review → Published → Deprecated → Archived
                                 ↓
                              Rollback
```

### 4.2 版本 API

```
GET    /api/v1/ecos/ontologies/{ontologyId}/versions                 — 版本列表
POST   /api/v1/ecos/ontologies/{ontologyId}/versions                 — 创建新版本
GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}     — 版本详情
POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/publish   — 发布版本
POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/rollback  — 回滚
POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/deprecate — 废弃
GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{v1}/diff/{v2}  — 版本对比
```

### 4.3 发布流程

```
Designer 点击发布
    ↓
预校验（命名规范、循环检测、完整性检查）
    ↓
创建 Snapshot（备份当前全部 Entity/Property/Relationship/Action/Rule）
    ↓
生成版本号（Major.Minor.Patch）
    ↓
状态: Draft → Published
    ↓
通知 Object Runtime 同步 Schema
```

### 4.4 版本策略

```
Major: 实体新增/删除、关系变更
Minor: 属性新增/删除、Action 新增
Patch: 属性默认值修改、描述修改、规则调整
```

---

## 5. 数据库设计

### 5.1 新增表

#### 5.1.1 `ecos_domain` — 领域
```sql
CREATE TABLE ecos_domain (
    id              VARCHAR(50) PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    owner           VARCHAR(100),
    description     TEXT,
    status          VARCHAR(50) DEFAULT 'Draft',    -- Draft/Published/Deprecated
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
```

#### 5.1.2 `ecos_ontology_property` — 增强
```sql
-- 在现有表基础上新增列:
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS enum_values TEXT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS default_value VARCHAR(500);
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS validation_rule TEXT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS unique_flag INT DEFAULT 0;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS ref_entity_code VARCHAR(100);
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS max_length INT;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS min_value NUMERIC;
ALTER TABLE ecos_ontology_property ADD COLUMN IF NOT EXISTS max_value NUMERIC;
```

#### 5.1.3 `ecos_ontology_rule` — 规则
```sql
CREATE TABLE ecos_ontology_rule (
    id              VARCHAR(50) PRIMARY KEY,
    entity_id       VARCHAR(50) NOT NULL,            -- 关联实体
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    rule_type       VARCHAR(50) NOT NULL,            -- VALIDATION/CALCULATION/DECISION/AGENT
    expression      TEXT NOT NULL,                   -- 规则表达式
    action          TEXT,                            -- 规则动作
    priority        INT DEFAULT 0,
    enabled         INT DEFAULT 1,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (entity_id) REFERENCES ecos_ontology_entity(id)
);
CREATE UNIQUE INDEX idx_rule_code ON ecos_ontology_rule(entity_id, code);
```

#### 5.1.4 `ecos_ontology_version` — 版本
```sql
CREATE TABLE ecos_ontology_version (
    id              VARCHAR(50) PRIMARY KEY,
    ontology_id     VARCHAR(50) NOT NULL,
    version_no      VARCHAR(20) NOT NULL,            -- Major.Minor.Patch
    status          VARCHAR(50) DEFAULT 'Draft',     -- Draft/Review/Published/Deprecated/Archived
    snapshot        JSONB NOT NULL,                  -- 完整快照: {entities:[], properties:[], relationships:[], actions:[], rules:[]}
    change_log      TEXT,                            -- 变更说明
    publisher       VARCHAR(100),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(ontology_id, version_no)
);
```

---

## 6. 可视化设计器前端组件清单

### 6.1 页面结构

```
Ontology Designer
├── Domain Center               — 领域管理主页
├── Entity Designer             — 实体设计器
│   ├── EntityListPanel         — 实体列表
│   ├── EntityForm              — 实体编辑表单
│   ├── PropertyTable           — 属性管理子面板
│   ├── RelationshipCanvas      — 关系编辑子面板（可视化连线）
│   ├── ActionDesigner          — 动作设计子面板
│   └── RuleDesigner            — 规则设计子面板
├── Ontology Explorer           — 全局本体浏览
├── Ontology Graph              — 全局关系图谱
├── Version Center              — 版本管理中心
├── Publish Center              — 发布中心
└── Governance Dashboard        — 治理面板
```

### 6.2 核心组件清单

| 组件名称 | 技术方案 | 说明 |
|---------|---------|------|
| **DomainCard** | Ant Design Card | 领域卡片列表，展示 code/name/owner/status |
| **DomainLifecycleBadge** | 自定义 Badge | Draft→Published→Deprecated 状态流转 |
| **EntityTreePanel** | Ant Design Tree | 以 Domain→Entity 层次树展示 |
| **EntityForm** | Ant Design Form | 实体 CRUD 表单，含 entityType 下拉 |
| **PropertyTable** | Ant Design Table (可编辑) | 行内编辑属性，支持拖拽排序 |
| **PropertyTypeSelector** | Ant Design Select | 属性类型选择，根据类型显示不同配置项 |
| **EnumValueEditor** | Ant Design Tag + Input | 枚举值动态增删编辑 |
| **RelationshipCanvas** | AntV G6 / ReactFlow | 可视化关系编辑，拖拽连线创建关系 |
| **RelationshipConfigPanel** | Ant Design Drawer | 点击连线弹出关系配置面板 |
| **ActionConfigPanel** | Ant Design Form + JSON Editor | 配置动作的前置条件/后置效果 |
| **RuleExpressionEditor** | CodeMirror / Monaco Editor | 规则表达式编辑器，支持语法高亮 |
| **RuleTestPanel** | 抽屉式面板 | 输入测试数据，实时评估规则结果 |
| **GraphExplorer** | AntV G6 (力导向图) | 全局本体图谱，节点=Entity，边=Relationship，支持缩放/展开 |
| **VersionTimeline** | Ant Design Timeline | 版本时间线展示 |
| **VersionDiff** | Monaco Diff Editor | 两个版本的 JSON 对比 |
| **PublishFlow** | Ant Design Steps | 发布流程步骤条（校验→审批→发布） |
| **GovernanceScore** | Ant Design Statistic | 治理评分仪表盘 |
| **CycleDetector** | 后台 DFS 算法 | 关系循环检测，前端高亮显示循环路径 |

### 6.3 设计器交互流程

```
1. 用户进入 Domain Center → 选择/创建 Domain
2. 进入 Entity Designer → 创建 Entity（选择 MASTER/TRANSACTION/EVENT/REFERENCE）
3. 在 PropertyTable 中添加属性 → 选择类型，填写约束
4. 在 RelationshipCanvas 中拖拽连线 → 配置关系类型和基数
5. 在 ActionConfigPanel 中添加动作 → 配置前置条件和后置效果
6. 在 RuleExpressionEditor 中编写规则 → 测试规则
7. 点击发布 → 校验 → 创建 Snapshot → 发布
```

---

## 7. 错误码

| 错误码 | 说明 | HTTP Status |
|--------|------|-------------|
| ONT-001 | 实体不存在 | 404 |
| ONT-002 | 属性重复 | 409 |
| ONT-003 | 关系循环 | 400 |
| ONT-004 | 规则错误 | 400 |
| ONT-005 | 发布失败 | 500 |
| ONT-006 | 版本冲突 | 409 |
| ONT-007 | 权限不足 | 403 |
| ONT-008 | Domain 不存在 | 404 |
| ONT-009 | Domain 编码重复 | 409 |

---

## 8. Sprint 1 交付范围

| 优先级 | 功能 | 说明 |
|--------|------|------|
| 🔴 P0 | Domain Management | `ecos_domain` 表 + 完整 CRUD + 生命周期 |
| 🔴 P0 | Rule Designer | `ecos_ontology_rule` 表 + CRUD + 测试 API |
| 🔴 P0 | Ontology Versioning | `ecos_ontology_version` 表 + Snapshot + diff |
| 🔴 P0 | Ontology Publishing | 发布流程：校验→Snapshot→版本号→发布 |
| 🔴 P0 | Action Designer 增强 | 前置条件/后置效果/权限绑定 |
| 🟡 P1 | Property Designer 增强 | 枚举/默认值/校验表达式/扩展类型字段 |
| 🟡 P1 | Relationship 循环检测 | 后台 DFS 循环检测 API |
| 🟡 P1 | Ontology Explorer | 领域→实体树形浏览 |
| 🟡 P1 | Ontology Graph | 全局关系图谱数据 API + G6 可视化 |
| 🟢 P2 | Governance Dashboard | 命名规范检查/重复实体检测/废弃属性检测 |
| 🟢 P2 | Agent Assistant | Agent 辅助生成实体/推荐关系 |

---

## 9. 架构决策记录 (ADR)

### ADR-01: Snapshot 存储方案
- **决策**: 版本快照以 JSONB 存储在 `ecos_ontology_version.snapshot`
- **理由**: PostgreSQL JSONB 支持索引和查询；单表完成版本管理；不依赖版本分支（不同于 Git 模型）
- **替代方案**: 每次变更记录 diff — 回滚时需要重新计算，复杂度高

### ADR-02: 规则引擎方案
- **决策**: Sprint 1 使用 Drools 语法做表达式解析 + 自定义轻量级规则执行器
- **理由**: Drools 语法成熟但全量引入太重；Sprint 1 规则以表达式形式为主（sales_amount > 10000000）
- **未来**: 规则量增大后可引入完整 Drools 引擎

### ADR-03: Property 增强方式
- **决策**: ALTER TABLE 扩展现有 `ecos_ontology_property` 表
- **理由**: 属性定义属于同一业务实体，无需分表；新增列向下兼容，不影响已有数据
