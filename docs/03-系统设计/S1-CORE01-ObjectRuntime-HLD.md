# S1-CORE01: Object Runtime 全链路 HLD

> **版本**: v1.0  
> **日期**: 2025-06-17  
> **作者**: ECOS-ARCH  
> **来源**: 第7章 Object Runtime 功能设计文档 (1027第7章 对象运行时.txt, 1266行)

---

## 1. 模块定位

Object Runtime 是 ECOS 的**业务运行核心**，负责将 Ontology 定义的业务语义（Entity/Property/Relationship/Action）实例化为可运行对象（Object），并提供对象全生命周期管理。

```
Ontology → Object Runtime → Workflow → Agent → Mission Control
       Entity → Object → Action → Workflow → Business Process
```

对应 Palantir Foundry Object Runtime。

---

## 2. 现有代码分析

### 2.1 现有 ObjectController 端点 (`/api/v1/ecos/objects`)

| 方法 | 路径 | 功能 | 差距 |
|------|------|------|------|
| GET | `/{entityCode}` | 对象列表（分页+关键词） | ✅ 基本满足，需增加高级过滤 |
| GET | `/{entityCode}/{id}` | 详情+关联+时间线 | ⚠️ 关联/timeline仅从旧表读取，缺关系图谱 |
| GET | `/{entityCode}/schema` | 实体Schema | ⚠️ 从 information_schema 读，未对接 Ontology |
| GET | `/search` | 全局搜索 | ⚠️ 仅关键词，缺语义搜索 |
| POST | `/{entityCode}` | 创建对象 | ⚠️ 缺规则校验、事件触发、状态机初始化 |
| PUT | `/{entityCode}/{id}` | 更新对象 | ⚠️ 缺版本记录、事件发布 |
| PUT | `/{entityCode}/{id}/status` | 状态变更 | ❌ **仅简单set，无状态机流转验证** |
| DELETE | `/{entityCode}/{id}` | 删除对象 | ⚠️ 缺软删除/归档支持 |

### 2.2 严重缺陷清单

| 编号 | 缺陷 | 影响 | 优先级 |
|------|------|------|--------|
| GAP-01 | **无状态机** — 当前仅允许任意 status 值直接写入 | 状态可任意跳转，业务逻辑失控 | 🔴 P0 |
| GAP-02 | **无关系图谱 API** — 仅读取 ontology_relationship 表 | 缺少图遍历、路径分析、关系 CRUD | 🔴 P0 |
| GAP-03 | **Timeline 是审计日志旁路** — 用 LIKE 匹配 audit_log | 不精确、无结构化事件 | 🔴 P0 |
| GAP-04 | **无版本管理** — 更新直接覆盖 | 无变更历史、无法回滚 | 🟡 P1 |
| GAP-05 | **无 Action 执行端点** — 虽有 Action 定义(OntologyActionController) | 对象详情页无法触发 Action | 🔴 P0 |
| GAP-06 | **Entity→Table 硬编码** — 仅支持3个demo表 | 不支持动态 Ontology | 🟡 P1 |
| GAP-07 | **无事件总线** — 创建/更新/删除无事件发布 | Workflow无法被对象事件触发 | 🔴 P0 |
| GAP-08 | **无附件管理** — 设计文档要求文件上传/预览 | 对象无法关联文件 | 🟡 P1 |
| GAP-09 | **无批量操作** — 仅单条CRUD | 批量审批/删除无法执行 | 🟢 P2 |
| GAP-10 | **无权限细粒度控制** — 缺少 Object/Property/Action/Relationship 四级权限 | 安全模型不足 | 🟡 P1 |

---

## 3. 新增 API 合约

### 3.1 完整端点清单

```
基础 CRUD（已有，需增强）:
GET    /api/v1/ecos/objects/{entityCode}                       — 列表（新增高级过滤参数）
GET    /api/v1/ecos/objects/{entityCode}/{id}                  — 详情
GET    /api/v1/ecos/objects/{entityCode}/schema                — Schema（改为读Ontology）
POST   /api/v1/ecos/objects/{entityCode}                       — 创建
PUT    /api/v1/ecos/objects/{entityCode}/{id}                  — 更新
DELETE /api/v1/ecos/objects/{entityCode}/{id}                  — 删除（软删除）

状态机流转（新增）:
GET    /api/v1/ecos/objects/{entityCode}/{id}/transitions      — 查询可用状态转换
POST   /api/v1/ecos/objects/{entityCode}/{id}/transition       — 执行状态转换

关系图谱（新增）:
GET    /api/v1/ecos/objects/{entityCode}/{id}/relationships    — 查询对象关系图
POST   /api/v1/ecos/objects/{entityCode}/{id}/relationships    — 创建对象间关系
DELETE /api/v1/ecos/objects/{entityCode}/{id}/relationships/{relId} — 删除关系
GET    /api/v1/ecos/objects/{entityCode}/{id}/graph            — 获取以该对象为中心的关系图谱（N层展开）

Timeline（新增）:
GET    /api/v1/ecos/objects/{entityCode}/{id}/timeline         — 结构化时间线（替代旧 LIKE 查询）

Action 执行（新增）:
POST   /api/v1/ecos/objects/{entityCode}/{id}/actions/{actionCode} — 对对象执行 Action

版本管理（新增）:
GET    /api/v1/ecos/objects/{entityCode}/{id}/versions         — 版本列表
GET    /api/v1/ecos/objects/{entityCode}/{id}/versions/{ver}   — 特定版本
POST   /api/v1/ecos/objects/{entityCode}/{id}/rollback         — 回滚到指定版本

附件管理（新增）:
GET    /api/v1/ecos/objects/{entityCode}/{id}/attachments      — 附件列表
POST   /api/v1/ecos/objects/{entityCode}/{id}/attachments      — 上传附件
DELETE /api/v1/ecos/objects/{entityCode}/{id}/attachments/{attId} — 删除附件

批量操作（新增）:
POST   /api/v1/ecos/objects/{entityCode}/batch                 — 批量操作（删除/状态变更）

事件总线（新增）:
（内部消息队列，非 REST API）

搜索增强（新增）:
GET    /api/v1/ecos/objects/search/semantic                     — 语义搜索（Agent辅助）
```

### 3.2 关键 API 契约详情

#### 3.2.1 状态机流转

```
GET /api/v1/ecos/objects/{entityCode}/{id}/transitions
Response:
{
  "currentStatus": "Draft",
  "availableTransitions": [
    {"to": "Submitted", "label": "提交", "requireRole": "Submitter"},
    {"to": "Archived", "label": "归档", "requireRole": "Admin"}
  ]
}

POST /api/v1/ecos/objects/{entityCode}/{id}/transition
Request:
{
  "transition": "Submit",
  "comment": "客户资料已完善"
}
Response:
{
  "previousStatus": "Draft",
  "newStatus": "Submitted",
  "timestamp": "2025-06-17T10:30:00Z"
}
```

#### 3.2.2 关系图谱

```
GET /api/v1/ecos/objects/{entityCode}/{id}/graph?depth=2
Response:
{
  "centerNode": {"id": "CUST001", "entityCode": "Customer", "label": "华为技术有限公司"},
  "nodes": [
    {"id": "ORD001", "entityCode": "Order", "label": "PO-2025-001"},
    {"id": "INV001", "entityCode": "Invoice", "label": "INV-2025-001"}
  ],
  "edges": [
    {"source": "CUST001", "target": "ORD001", "label": "PLACED", "type": "OneToMany"},
    {"source": "ORD001", "target": "INV001", "label": "GENERATED", "type": "OneToOne"}
  ],
  "depth": 2,
  "totalNodes": 3,
  "totalEdges": 2
}
```

#### 3.2.3 Action 执行

```
POST /api/v1/ecos/objects/{entityCode}/{id}/actions/Approve
Request:
{
  "params": {"comment": "审批通过", "nextAssignee": "user002"}
}
Response:
{
  "action": "Approve",
  "status": "executed",
  "objectStatus": "Approved",
  "events": ["ObjectApproved", "WorkflowTriggered"]
}
```

#### 3.2.4 Timeline

```
GET /api/v1/ecos/objects/{entityCode}/{id}/timeline?page=1&size=50
Response:
{
  "data": [
    {
      "eventId": "evt-001",
      "eventType": "ObjectCreated",
      "timestamp": "2025-01-01T09:00:00Z",
      "actor": "user001",
      "summary": "对象创建",
      "details": {"createdBy": "张三"}
    },
    {
      "eventType": "ActionExecuted",
      "timestamp": "2025-01-05T14:30:00Z",
      "actor": "user002",
      "summary": "Approve 操作执行",
      "details": {"action": "Approve", "comment": "审批通过"}
    }
  ],
  "total": 25
}
```

---

## 4. 数据库设计

### 4.1 新增表

#### 4.1.1 `ecos_object_state_machine` — 状态机定义
```sql
CREATE TABLE ecos_object_state_machine (
    id              VARCHAR(50) PRIMARY KEY,
    entity_code     VARCHAR(100) NOT NULL,          -- 关联实体
    from_status     VARCHAR(50) NOT NULL,            -- 源状态
    to_status       VARCHAR(50) NOT NULL,            -- 目标状态
    transition_code VARCHAR(100) NOT NULL,           -- 转换动作编码 (e.g. "Submit")
    transition_name VARCHAR(200),                    -- 转换动作名称 (e.g. "提交")
    require_role    VARCHAR(200),                    -- 所需角色
    guard_rule      TEXT,                            -- 守卫规则 (JSON expression)
    side_effect     TEXT,                            -- 副作用 (JSON: 触发事件/通知)
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(entity_code, from_status, transition_code)
);
```

#### 4.1.2 `ecos_object_timeline` — 结构化时间线
```sql
CREATE TABLE ecos_object_timeline (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,           -- 对象ID
    entity_code     VARCHAR(100) NOT NULL,           -- 实体代码
    event_type      VARCHAR(100) NOT NULL,           -- 事件类型: ObjectCreated/ObjectUpdated/ActionExecuted/StatusChanged/WorkflowStarted
    event_summary   VARCHAR(500),                    -- 事件摘要
    actor           VARCHAR(100),                    -- 操作人
    details         JSONB,                           -- 事件详情
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_timeline_object ON ecos_object_timeline(object_id, created_at DESC);
CREATE INDEX idx_timeline_entity ON ecos_object_timeline(entity_code, created_at DESC);
```

#### 4.1.3 `ecos_object_version` — 对象版本
```sql
CREATE TABLE ecos_object_version (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,
    entity_code     VARCHAR(100) NOT NULL,
    version_no      INT NOT NULL,                    -- 版本号 (1,2,3...)
    snapshot        JSONB NOT NULL,                  -- 完整快照
    change_summary  VARCHAR(500),                    -- 变更说明
    created_by      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(object_id, version_no)
);
```

#### 4.1.4 `ecos_object_relationship` — 对象实例关系
```sql
CREATE TABLE ecos_object_relationship (
    id                  VARCHAR(50) PRIMARY KEY,
    source_object_id    VARCHAR(100) NOT NULL,
    target_object_id    VARCHAR(100) NOT NULL,
    source_entity_code  VARCHAR(100) NOT NULL,
    target_entity_code  VARCHAR(100) NOT NULL,
    relationship_code   VARCHAR(100) NOT NULL,       -- 对应 Ontology 定义的关系
    relationship_type   VARCHAR(50) NOT NULL,        -- OneToOne/OneToMany/ManyToMany
    properties          JSONB,                       -- 关系属性
    created_at          TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_obj_rel_source ON ecos_object_relationship(source_object_id);
CREATE INDEX idx_obj_rel_target ON ecos_object_relationship(target_object_id);
```

#### 4.1.5 `ecos_object_attachment` — 附件
```sql
CREATE TABLE ecos_object_attachment (
    id              VARCHAR(50) PRIMARY KEY,
    object_id       VARCHAR(100) NOT NULL,
    entity_code     VARCHAR(100) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    file_path       VARCHAR(1000) NOT NULL,
    file_size       BIGINT,
    mime_type       VARCHAR(200),
    version_no      INT DEFAULT 1,
    uploaded_by     VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### 4.2 已有表无需改动

- `demo_customer`, `demo_supplier`, `demo_invoice` — 过渡期 demo 表，后续由 Ontology 配置驱动动态表
- `ecos_ontology_entity/property/relationship/action` — Ontology 模块维护，Object Runtime **只读**
- `td_audit_log` — 保留，但 Timeline 不再依赖 LIKE 查询

---

## 5. 与 Ontology / Workflow 模块的接口契约

### 5.1 Object Runtime → Ontology

| 用途 | 接口 | 调用时机 |
|------|------|----------|
| 获取实体 Schema（字段定义） | `GET /api/v1/ecos/ontologies/{ontologyId}/entities/{entityId}` + properties | 对象创建/编辑时，获取字段列表和校验规则 |
| 获取状态机定义 | `GET /api/v1/ecos/ontologies/{ontologyId}/entities/{entityId}/stateMachine` (新增) | 状态流转时查询允许的转换 |
| 获取可用 Action 列表 | `GET /api/v1/ecos/entities/{entityId}/actions` | 对象详情页 Action 面板渲染 |
| 获取关系定义 | `GET /api/v1/ecos/entities/{entityId}/relationships` | 对象关系图谱渲染 |
| 获取校验规则 | `GET /api/v1/ecos/ontologies/{ontologyId}/entities/{entityId}/rules` (新增) | 对象创建/更新时规则校验 |

### 5.2 Object Runtime → Workflow

| 用途 | 接口 | 调用时机 |
|------|------|----------|
| 触发工作流 | `POST /api/v1/ecos/workflows/{wfId}/start` (新增) | 对象创建/Action 执行后 |
| 查询关联流程 | `GET /api/v1/ecos/workflows/instances?objectId={id}` (新增) | 对象详情页 Workflow Tab |
| 事件发布 | 内部 `ApplicationEventPublisher` → Workflow 监听 | 对象状态变更事件 |

### 5.3 事件契约（Event Bus）

```json
// ObjectCreated
{"eventType":"ObjectCreated","objectId":"CUST001","entityCode":"Customer","timestamp":"...","payload":{...}}

// ObjectUpdated
{"eventType":"ObjectUpdated","objectId":"CUST001","entityCode":"Customer","changedFields":["name","phone"],"timestamp":"..."}

// StatusChanged
{"eventType":"StatusChanged","objectId":"CUST001","fromStatus":"Draft","toStatus":"Submitted","timestamp":"..."}

// ActionExecuted
{"eventType":"ActionExecuted","objectId":"CUST001","actionCode":"Approve","result":"success","timestamp":"..."}
```

---

## 6. 前端组件建议

### 6.1 Object Explorer 页面

```
┌─────────────────────┐
│ Object Type Tree    │  ← 左侧导航：按 Domain/Entity 树形结构
├─────────────────────┤
│ Object List         │  ← 表格：ID/Name/Type/Status/Owner/UpdatedTime
├─────────────────────┤
│ Filter/Search       │  ← 高级筛选 + 语义搜索条
└─────────────────────┘
```

组件清单:
- **EntityTreePanel**: 按 Domain → Entity 展示对象类型树，Ant Design Tree
- **ObjectTable**: 带分页/排序/筛选的数据表格，Ant Design Table
- **FilterBar**: 高级筛选（状态/日期/标签），支持保存筛选条件
- **BatchActionBar**: 选中多对象后的批量操作工具栏
- **SemanticSearchBox**: Agent 辅助的语义搜索输入框

### 6.2 Object Detail 页面

Tab 结构:
1. **Summary** — 对象名称、状态徽章、Owner、评分、标签
2. **Properties** — 表单式属性展示/编辑，按 Ontology Property 定义渲染
3. **Relationships** — 关系图谱可视化（使用 vis.js / cytoscape.js / AntV G6）
4. **Actions** — Action 按钮面板（Approve/Reject/Suspend 等），需权限校验
5. **Timeline** — 结构化时间线（Ant Design Timeline 组件）
6. **Files** — 附件列表 + 上传
7. **Workflow** — 关联工作流列表 + 启动
8. **Knowledge** — 关联知识文档
9. **Audit** — 审计日志

关键组件:
- **StatusBadge**: 展示当前状态 + 可点击下拉的状态转换菜单
- **RelationshipGraph**: 基于 G6 的力导向图，支持展开/收缩/路径高亮
- **ActionPanel**: 根据 Object 当前状态动态渲染可用 Action 按钮
- **TimelineFeed**: 按时间倒序的事件流
- **VersionDiff**: 版本对比 diff 视图

### 6.3 Object Creation 页面

- 根据 Entity Schema 动态渲染表单字段
- 实时规则校验提示
- 提交前规则引擎校验

---

## 7. 错误码

| 错误码 | 说明 | HTTP Status |
|--------|------|-------------|
| OBJ-001 | 对象不存在 | 404 |
| OBJ-002 | 状态非法（状态机不允许该转换） | 400 |
| OBJ-003 | Action 不可执行（权限/状态不满足） | 403 |
| OBJ-004 | 关系不存在 | 404 |
| OBJ-005 | 权限不足 | 403 |
| OBJ-006 | 对象重复 | 409 |
| OBJ-007 | 版本冲突 | 409 |
| OBJ-008 | Workflow 启动失败 | 500 |

---

## 8. Sprint 1 交付范围

| 优先级 | 功能 | 说明 |
|--------|------|------|
| 🔴 P0 | 状态机引擎 | `ecos_object_state_machine` 表 + transition API + 状态流转验证 |
| 🔴 P0 | 关系图谱 API | `ecos_object_relationship` 表 + graph 端点 + CRUD |
| 🔴 P0 | 结构化 Timeline | `ecos_object_timeline` 表替代 LIKE 查询 |
| 🔴 P0 | Action 执行端点 | `POST /{id}/actions/{actionCode}` |
| 🔴 P0 | 事件总线 | 创建/更新/状态变更/action 执行时发布 Spring Event |
| 🟡 P1 | 版本管理 | `ecos_object_version` 表 + 版本 API |
| 🟡 P1 | 附件管理 | `ecos_object_attachment` 表 + 上传/下载 API |
| 🟡 P1 | 动态实体 Schema | 从 Ontology 表读取代替硬编码 ENTITY_TABLE |
| 🟢 P2 | 批量操作 | batch 端点 |
| 🟢 P2 | 语义搜索 | Agent 辅助搜索 |

---

## 9. 架构决策记录 (ADR)

### ADR-01: 状态机独立表 vs JSON 字段
- **决策**: 使用独立表 `ecos_object_state_machine`
- **理由**: 便于管理/查询/审计；JSON 字段难以做约束和索引
- **替代方案**: 在 Entity 定义中嵌入 JSON 状态机定义 — 灵活性高但查询困难

### ADR-02: Timeline 独立表 vs 依赖审计日志
- **决策**: 新独立表 `ecos_object_timeline`，审计日志保留做合规审计
- **理由**: Timeline 侧重业务展示（结构化、可扩展）；审计日志侧重合规（不可篡改）

### ADR-03: 关系图谱存储
- **决策**: 使用 PostgreSQL 关系表存储，配合递归 CTE 实现 N 层展开
- **理由**: Sprint 1 不引入图数据库（如 Neo4j）；PostgreSQL 足以支持中等规模图查询
- **未来**: 大规模场景可迁移至 Neo4j / JanusGraph
