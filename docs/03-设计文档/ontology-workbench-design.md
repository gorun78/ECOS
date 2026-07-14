# ECOS 本体工作台 (Enterprise Ontology Workbench) 整合设计方案

> **版本**: v1.3  
> **日期**: 2026-07-02  
> **作者**: ECOS-ARCH + ECOS-PM + ECOS-PMO  
> **来源**: 整合 KnowledgeGraphPage / OntologyDesigner / OntologyExplorer 三个独立模块。v1.3 重构：明确「本体≈业务域」、单页工作台、左面板集成实体CRUD+术语+规则、画布切换全局/域视图、打通数据底座映射。

---

## 目录

0. [核心概念体系定义](#0-核心概念体系定义)
1. [设计目标与范围](#1-设计目标与范围)
2. [当前三个模块现状分析](#2-当前三个模块现状分析)
3. [整合架构总览](#3-整合架构总览)
4. [路由设计](#4-路由设计)
5. [组件树设计](#5-组件树设计)
6. [数据流设计](#6-数据流设计)
7. [核心页面布局设计](#7-核心页面布局设计)
8. [后端API兼容策略](#8-后端api兼容策略)
9. [状态管理设计](#9-状态管理设计)
10. [关键交互流程](#10-关键交互流程)
11. [术语库集成](#11-术语库集成)
12. [数据映射与数据同步](#12-数据映射与数据同步)
13. [组件规格详表](#13-组件规格详表)
14. [迁移策略与向后兼容](#14-迁移策略与向后兼容)
15. [交付计划](#15-交付计划)
16. [附录：文件清单](#16-附录文件清单)

---

## 0. 核心概念体系定义

> **v1.3 核心决策**：**本体 (Ontology) ≈ 业务域 (Domain)**。在 ECOS 语境下，一个业务域对应一个本体。域是组织视角的称呼，本体是技术视角的称呼，两者指代同一事物。不引入额外的"本体"抽象层。

### 0.1 概念层次总图（v1.3 简化）

```
                        DIKW 金字塔
┌─────────────────────────────────────────────────────────────────────┐
│  Wisdom (W)    │  战略目标 / 因果链 / 决策规则      │  为什么       │
├─────────────────────────────────────────────────────────────────────┤
│  Knowledge (K) │  域=本体 / 实体 / 属性 / 关系 / 术语/ 规则 │ 是什么 │
├─────────────────────────────────────────────────────────────────────┤
│  Information (I)│  对象 / 状态机 / 事件             │  具体谁       │
├─────────────────────────────────────────────────────────────────────┤
│  Data (D)      │  数据集 / 物理表 / 视图 / 字段    │  存在哪       │
└─────────────────────────────────────────────────────────────────────┘
```

```
                        ┌──────────────────────┐
                        │   本体 = 业务域        │  公司业务板块
                        │   Ontology ≈ Domain   │  例：供应链本体=采购域
                        │   (两者等同)           │
                        └──────────┬───────────┘
                                   │ 1:N
                        ┌──────────▼───────────┐
                        │    实体 (Entity)       │  类型模板
                        │    "供应商"（非华为）   │
                        └──┬───┬───┬───┬──────┘
                           │   │   │   │
              ┌────────────┘   │   │   └────────────┐
              ▼                ▼   ▼                ▼
        ┌──────────┐   ┌──────────┐  ┌──────────┐  ┌──────────┐
        │   属性    │   │   关系    │  │   术语    │  │   规则    │
        │ Property  │   │Relation   │  │ Glossary  │  │  Rule    │
        └──────────┘   └──────────┘  └──────────┘  └──────────┘
                               │
                ┌──────────────┼──────────────┐
                │  ecos_entity_table_mapping  │  ← 映射桥
                └──────────────┼──────────────┘
                               │
                        ┌──────▼───────┐
                        │    数据集     │  物理存储容器
                        │   Dataset    │  例：supplier_master 表
                        └──────┬───────┘
                               │ 1:N
                        ┌──────▼───────┐
                        │    对象      │  具体实例记录
                        │   Object     │  "华为（SN001）"
                        └──────────────┘
```

### 0.2 概念定义速查卡（v1.3 更新）

| # | 概念 | 英文 | DIKW层 | 一句话定义 | 对应数据库表 |
|---|------|------|--------|-----------|-------------|
| 1 | **本体/业务域** | Ontology ≈ Domain | K | 公司业务板块 = 一个领域的词汇表+规则书 | `ecos_ontology` ≈ `ecos_domain` |
| 2 | **实体** | Entity | K | 业务对象的类型模板 | `ecos_ontology_entity` |
| 3 | **属性** | Property | K | 描述实体的字段定义 | `ecos_entity_property` |
| 4 | **关系** | Relationship | K | 实体间的语义连线 | `ecos_object_links` |
| 5 | **术语** | GlossaryTerm | K | 业务名词的标准化定义 | `ecos_glossary_term` |
| 6 | **规则** | Rule | K | 业务约束/推导规则 | 待建表 |
| 7 | **数据集** | Dataset | D | 物理存储的数据容器 | `td_data_resource` |
| 8 | **对象** | Object | I | 具体实例记录 | `ecos_object_data` |

> ⚠️ **v1.3 关键变化**：Domain 和 Ontology 合并为一个概念。`ecos_domain` 和 `ecos_ontology` 通过 `domain.ontology_id` 关联（1:1 或 N:1），前端统一展示为「本体/业务域」。

### 0.3 关键概念辨析

#### 0.3.1 实体 vs 数据集（最重要）

| 维度 | 实体 (Entity) | 数据集 (Dataset) |
|------|-------------|----------------|
| **是什么** | 业务概念的类型定义 | 物理存储的数据容器 |
| **类比** | Excel 的**表头行**（列名+类型） | Excel 的**Sheet 页**（存实际数据） |
| **DIKW 层** | Knowledge (K) | Data (D) |
| **内容** | 属性名、类型、约束、关系的语义定义 | 实际数据行、物理字段、存储引擎 |
| **谁会改** | 业务分析师定义业务语义 | DBA/数据工程师管理物理存储 |
| **示例** | 「供应商」有属性：名称、税号、联系人 | `supplier_master` 表有字段：name VARCHAR(200), tax_id VARCHAR(50) |
| **关联方式** | 通过 `ecos_entity_table_mapping` 映射桥关联 |

```
实体「供应商」          数据集「supplier_master」
┌─────────────────┐    ┌─────────────────────────┐
│ 名称: 文本       │    │ id | name  | tax_id    │
│ 税号: 字符串     │ ←→ │ 1  | 华为   | 914403... │
│ 联系人: 文本     │    │ 2  | 中兴   | 914401... │
│ 信用等级: 枚举   │    │ 3  | 腾讯   | 914403... │
└─────────────────┘    └─────────────────────────┘
   G2 业务语义层            G1 物理数据层
```

> ⚠️ **实体不是数据集**。实体定义"供应商应该有什么属性"，数据集存储"供应商的实际数据记录"。

#### 0.3.2 本体 vs 实体

| 维度 | 本体 (Ontology) | 实体 (Entity) |
|------|----------------|--------------|
| 粒度 | 域级别 | 概念级别 |
| 关系 | 1 个本体包含 N 个实体 | 1 个实体只属于 1 个本体 |
| 类比 | 一本书的「目录 + 术语表」 | 书里「某一章的标题」 |
| 示例 | 「供应链本体」涵盖供应商、物料、合同… | 其中「供应商」是一个实体 |

#### 0.3.3 实体 vs 对象

| 维度 | 实体 (Entity) | 对象 (Object) |
|------|-------------|--------------|
| 是什么 | 类型模板 | 具体实例 |
| DIKW 层 | K | I |
| 类比 | 「人类」这个物种定义 | 「张三」这个具体的人 |
| 数量 | 全系统约 13 个实体 | 全系统约 44 个对象 |

#### 0.3.4 术语 vs 实体

| 维度 | 术语 (GlossaryTerm) | 实体 (Entity) |
|------|-------------------|--------------|
| 是什么 | 业务名词的词典解释 | 业务概念的类型模板 |
| 类比 | 书末「名词解释」 | 书里「章节内容」 |
| 示例 | 术语"供应商"：依法向采购人提供货物/服务的法人 | 实体"供应商"：有名称、税号、联系人等属性的类型定义 |

#### 0.3.5 属性 vs 字段

| 维度 | 属性 (Property) | 字段 (Field/Column) |
|------|----------------|-------------------|
| 是什么 | 实体的业务语义描述 | 数据集中的物理列 |
| DIKW 层 | K | D |
| 示例 | 实体「供应商」.属性「税号」(业务约束：必填，唯一) | 表 `supplier_master`.字段 `tax_id VARCHAR(50) NOT NULL UNIQUE` |

### 0.4 当前系统已知偏差

以下偏差是系统当前实际状态与理想概念模型之间的差距，需在后续 Sprint 中修正：

| # | 偏差 | 严重度 | 现状 | 修正方向 |
|---|------|--------|------|----------|
| **1** | `ONTOLOGY_ID="ont001"` 写死 | 🔴 致命 | 前端 `CreateEntityModal` 写死 ont001，ont002（财务本体）完全不可见 | 改为从当前选中的域推导 ontology_id |
| **2** | Entity 双归属 `domain_id` + `ontology_id` | 🔴 致命 | 同一实体可能 domain_id=A 但 ontology_id 指向另一个域的本体 | 取消 domain_id（或设为冗余缓存），以 ontology→domain 为准 |
| **3** | `ecos_entity_table_mapping` 为空 | 🔴 致命 | 0 条记录，实体↔数据集映射桥完全缺失，G2↔G1 断层 | 实现 AutoDiscover 自动填充，或提供手动绑定 UI |
| **4** | 本体与域混用 | 🟡 严重 | Invoice 实体挂 ont002 但语义上属于 Project 域 | 实现本体选择器，允许多本体并存 |
| **5** | 数据源注册无本体关联 | 🟡 严重 | `td_data_resource` 注册时不关联任何实体 | 数据资源注册流程增加「关联实体」步骤 |
| **6** | 术语无实体绑定 | 🟡 严重 | 术语独立存在，无法从术语反向查到关联实体 | 术语详情页展示关联实体列表 |
| **7** | 对象无实体类型标识 | 🟡 严重 | `ecos_object_data` 无 `entity_id` 字段 | 对象注册时绑定实体类型 |
| **8** | 关系类型无约束 | 🟢 中等 | 任意两个实体可建立任意类型关系，无 schema 校验 | 关系建立时校验实体类型配对是否合法 |
| **9** | 属性类型无规范 | 🟢 中等 | 属性类型自由文本，无枚举约束 | 引入属性类型枚举（String/Number/Date/Boolean/Enum/Ref） |
| **10** | ont002 本体闲置 | 🟢 中等 | 财务本体 ont002 已创建但前端从未展示 | 多本体切换功能，前端展示所有本体 |

> **当前 Sprint 优先修正 #1、#2、#3**，其他逐 Sprint 推进。

### 0.5 G2 工作台五区映射

工作台五区与概念体系的对应关系：

```
┌────────────┬──────────┬───────────────┬────────────┬──────────────┐
│  左域面板   │ 左实体树  │   中画布       │  右详情面板 │  术语融合面板 │
│  Domain    │ Entity   │  ReactFlow    │  Property  │  Glossary    │
│  选择器    │  Tree    │  画布          │  Relation  │  Binding     │
│            │          │               │  DataMap   │              │
├────────────┼──────────┼───────────────┼────────────┼──────────────┤
│ 业务域 CRUD │ 实体列表  │ 实体节点+关系边 │ 属性编辑器  │ 术语关联     │
│ 本体信息    │ 属性概览  │ 数据映射连线   │ 关系列表    │ 术语搜索     │
│ 域统计      │          │ AutoDiscover  │ 数据映射    │              │
└────────────┴──────────┴───────────────┴────────────┴──────────────┘
```

---

## 1. 设计目标与范围

### 1.1 核心目标

将当前三个独立、功能重叠的本体相关模块整合为**一站式本体工作台 (Enterprise Ontology Workbench)**，以「业务域 (Domain)」为顶层导航单位，提供从知识图谱总览 → 业务域选择 → 可视化本体设计的完整工作流。同时，融入**术语库 (Glossary)** 集成能力和**数据映射/同步 (Data Mapping & Sync)** 能力，连通「业务语义 → 本体设计 → 物理数据」三层。

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **Domain First** | 一切本体归属业务域，以域为导航入口 |
| **Visual First** | 图形化画布为核心交互界面，拖拽连线式本体建模 |
| **API Backward Compatible** | 100% 复用现有 `/api/v1/ecos/knowledge-graph`、`/api/v1/ecos/ontology/*`、`/api/glossary/*`、`/api/v1/datanet/metadata/*` |
| **Progressive Integration** | 逐步替换旧路由，保留向后兼容别名 |
| **Separation of Concerns** | 左导航 + 中画布 + 右属性 三栏经典布局 |
| **Semantic-to-Physical Link** | 本体实体可通过术语库挂载业务语义，通过数据映射挂载物理表/视图 |

### 1.3 不在范围

- 后端 API 的修改或新增（本次仅前端整合）
- Domain 管理 API 的新增（后续 Sprint）
- 规则引擎 / 动作设计器（后续 Sprint）
- 版本管理与发布流程（后续 Sprint）
- 术语库后端的修改（前端集成，复用现有 API）
- 数据底座后端的修改（前端集成，复用现有 API）

---

## 2. 当前三个模块现状分析

### 2.1 模块对比

| 维度 | KnowledgeGraphPage | OntologyDesigner | OntologyExplorer |
|------|-------------------|-----------------|------------------|
| **路由** | `/knowledge_graph` | `/ontology_designer` | `/ontology` |
| **代码行数** | ~123 行 | ~412 行 | ~426 行 |
| **后端 API** | `GET /api/v1/ecos/knowledge-graph` | 10+ CRUD 端点 | `fetchOntology` |
| **核心功能** | 按业务域分组展示实体+关系（只读） | 实体/属性/关系 CRUD + EntityGraph | 浏览实体定义+实例+动作 |
| **可视化** | 无图形画布 | EntityGraph 子组件（可连边） | GraphCanvas（只读浏览） |
| **领域概念** | 按 domain 分组展示 | ❌ 无 domain，实体平铺 | ❌ 无 domain |
| **交互能力** | 纯只读，无可编辑 | ✅ 完整 CRUD | 只读浏览+触发动作 |
| **主要问题** | 无交互，功能单一 | 无 domain，实体混乱 | 与设计器功能重叠 |

### 2.2 现有后端 API 端点（需完全兼容）

#### 知识图谱 API
```
GET /api/v1/ecos/knowledge-graph
→ Response: { nodes: [...], edges: [...], stats: {...} }
```

#### 本体 CRUD API（以 ontologyId = "ont001" 为例）
```
GET    /api/v1/ecos/ontology/ont001/entities           → 实体列表
POST   /api/v1/ecos/ontology/ont001/entities           → 创建实体
PUT    /api/v1/ecos/ontology/ont001/entities/{id}      → 更新实体
DELETE /api/v1/ecos/ontology/ont001/entities/{id}      → 删除实体

GET    /api/v1/ecos/ontology/ont001/entities/{id}/properties  → 属性列表
POST   /api/v1/ecos/ontology/ont001/entities/{id}/properties  → 创建属性
PUT    /api/v1/ecos/ontology/ont001/entities/{id}/properties/{pid} → 更新属性
DELETE /api/v1/ecos/ontology/ont001/entities/{id}/properties/{pid} → 删除属性

GET    /api/v1/ecos/ontology/ont001/relationships      → 关系列表
POST   /api/v1/ecos/ontology/ont001/relationships      → 创建关系
DELETE /api/v1/ecos/ontology/ont001/relationships/{id} → 删除关系
```

#### 术语库 API（v1.1 新增依赖）
```
GET    /api/glossary/terms?domain=&status=    → 术语列表
POST   /api/glossary/terms                    → 创建术语
PUT    /api/glossary/terms/{id}               → 更新术语
DELETE /api/glossary/terms/{id}               → 删除术语
```

#### 数据底座 API（v1.1 新增依赖）
```
GET /api/v1/datanet/metadata/resources/all         → 全部数据资源（表/视图）
GET /api/v1/datanet/metadata/fields/{resourceId}   → 表字段列表
GET /api/v1/datanet/metadata/preview/{resourceId}  → 预览数据行
```

#### 对象浏览器 API（v1.1 新增依赖）
```
GET /api/v1/ecos/objects/{entityCode}              → 对象列表（分页）
GET /api/v1/ecos/objects/{entityCode}/schema       → 对象Schema（属性定义）
GET /api/v1/ecos/objects/{entityCode}/{id}          → 对象详情
```

### 2.3 可复用现有组件

| 组件 | 来源 | 复用方式 |
|------|------|----------|
| `GraphCanvas` | OntologyExplorer | 作为 WorkbenchCanvas 基础，添加编辑能力 |
| `EntityGraph` | OntologyDesigner | 抽取连线创建逻辑，融入新画布 |
| Property Table (可编辑) | OntologyDesigner | 直接迁移至右侧属性面板 |
| Entity 表单 | OntologyDesigner | 抽取为 EntityForm 组件 |

---

## 3. 整合架构总览

### 3.1 用户工作流

```
┌──────────────────────────────────────────────────────────────────┐
│                    本体工作台 (Ontology Workbench)                  │
│                                                                  │
│  Step 1            Step 2              Step 3                    │
│  ┌──────────┐     ┌──────────┐       ┌──────────────────────┐   │
│  │ 知识图谱  │ ──→ │ 业务域   │ ──→  │ 可视化本体设计器      │   │
│  │ 首页总览  │     │ 选择/创建 │       │ 拖拽节点 · 连线关系  │   │
│  └──────────┘     └──────────┘       │ 编辑属性 · 实时预览   │   │
│                                       │ 挂载术语 · 映射数据   │   │
│                                       └──────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 三栏布局

```
┌────────────┬──────────────────────────────┬─────────────────────┐
│  左侧面板   │        中间画布               │     右侧面板         │
│  (280px)   │       (flex: 1)              │     (320px)         │
│            │                              │                     │
│ ┌────────┐ │  ┌──────────────────────┐   │ ┌─────────────────┐ │
│ │域选择器 │ │  │                      │   │ │ 属性编辑器       │ │
│ │+ 创建域 │ │  │   可视化图形画布      │   │ │                 │ │
│ └────────┘ │  │   (ReactFlow)        │   │ │ · 基本信息       │ │
│            │  │                      │   │ │ · 属性列表       │ │
│ ┌────────┐ │  │  [Customer]          │   │ │ · 关联关系       │ │
│ │实体树   │ │  │    │                │   │ │ · 术语关联       │ │
│ │        │ │  │  [Order]──[Product]  │   │ │ · 数据源         │ │
│ │ ├ 客户  │ │  │    │                │   │ │ · 动作列表       │ │
│ │ ├ 订单  │ │  │  [Invoice]          │   │ └─────────────────┘ │
│ │ ├ 产品  │ │  │                      │   │                     │
│ │ └ ...   │ │  └──────────────────────┘   │                     │
│ └────────┘ │                              │                     │
└────────────┴──────────────────────────────┴─────────────────────┘
```

---

## 4. 路由设计

### 4.1 路由表

```
新路由                                旧路由（重定向/别名）
──────────────────────────────────  ──────────────────────────
/ontology-workbench                 ← /knowledge_graph
                                      (知识图谱首页，域总览模式)

/ontology-workbench/domains          ← /ontology_designer
                                      (域列表+选择)

/ontology-workbench/domains/:code    ← /ontology
                                      (进入指定域的设计器)
```

### 4.2 路由定义（React Router v6）

```typescript
// routes.tsx
const ontologyWorkbenchRoutes = [
  {
    path: '/ontology-workbench',
    element: <OntologyWorkbenchLayout />,
    children: [
      {
        index: true,
        element: <KnowledgeGraphHome />,     // Step 1: 图谱首页
      },
      {
        path: 'domains',
        element: <DomainListView />,         // Step 2: 域选择
      },
      {
        path: 'domains/:domainCode',
        element: <DomainDesignerView />,     // Step 3: 本体设计器
      },
    ],
  },
  // 旧路由重定向
  {
    path: '/knowledge_graph',
    element: <Navigate to="/ontology-workbench" replace />,
  },
  {
    path: '/ontology_designer',
    element: <Navigate to="/ontology-workbench/domains" replace />,
  },
  {
    path: '/ontology',
    element: <Navigate to="/ontology-workbench/domains" replace />,
  },
];
```

### 4.3 路由状态映射

| 路由路径 | 对应Step | 左侧面板 | 中间画布 | 右侧面板 |
|----------|---------|----------|----------|----------|
| `/ontology-workbench` | Step 1 | (隐藏) | 知识图谱全局总览（只读） | 统计面板 |
| `/ontology-workbench/domains` | Step 2 | 域列表 | 域卡片 + 实体统计 | (隐藏) |
| `/ontology-workbench/domains/:code` | Step 3 | 实体树 | 可编辑画布 | 属性编辑器 + 术语/数据源标签页 |

---

## 5. 组件树设计

### 5.1 完整组件树

```
OntologyWorkbenchLayout                        ← 顶层布局容器（三栏）
│
├── WorkbenchLeftPanel                         ← 左侧面板 (280px)
│   ├── DomainSelector                         ← 域选择器（当前域 + 切换下拉）
│   ├── DomainCreateButton                     ← 创建新域按钮
│   └── EntityTreePanel                        ← 实体树形导航
│       ├── EntityTreeNode                     ← 实体节点（递归）
│       │   ├── EntityIcon (按类型)            ← 图标：MASTER/TRANSACTION/EVENT/REFERENCE
│       │   └── EntityBadge (计数)             ← 属性数/关系数角标
│       └── EntitySearchBar                    ← 实体搜索过滤
│
├── WorkbenchCanvas                            ← 中间画布 (flex: 1)
│   ├── CanvasToolbar                          ← 画布工具栏
│   │   ├── AddEntityButton                    ← 添加实体
│   │   ├── LayoutButton                       ← 自动布局（力导向/层级/圆形）
│   │   ├── ZoomControls                       ← 缩放控制
│   │   ├── FitViewButton                      ← 自适应视图
│   │   └── FilterDropdown                     ← 类型/关系过滤
│   ├── KnowledgeGraphView                     ← Step 1: 全局图谱（只读）
│   │   └── GraphCanvas (readOnly=true)        ← 展示 nodes + edges
│   ├── DomainCardGrid                          ← Step 2: 域卡片网格
│   │   └── DomainCard                         ← 单个域卡片（名称/实体数/关系数/状态）
│   └── DomainGraphCanvas                      ← Step 3: 可编辑画布
│       ├── ReactFlow Instance                 ← 基于 ReactFlow 的画布
│       │   ├── EntityNode (custom)            ← 自定义实体节点（可拖拽）
│       │   │   ├── NodeHeader                 ← 实体名称 + 类型图标
│       │   │   ├── NodePreviewProperties      ← 预览前3个属性
│       │   │   └── GlossaryBadge (v1.1)       ← 已关联术语标识
│       │   └── RelationshipEdge (custom)      ← 自定义关系边
│       │       ├── EdgeLabel                  ← 关系名称/类型
│       │       └── EdgeControls               ← 编辑/删除按钮
│       └── CreateRelationshipModal            ← 创建关系弹窗
│           ├── SourceSelector                  ← 源实体选择
│           ├── TargetSelector                  ← 目标实体选择
│           ├── RelationshipTypeSelector        ← 关系类型选择
│           └── CardinalityConfig               ← 基数配置
│
└── WorkbenchRightPanel                        ← 右侧面板 (320px)
    ├── DomainStatsPanel                        ← Step 1/2: 域统计
    │   ├── EntityCountCard                     ← 实体总数
    │   ├── RelationshipCountCard               ← 关系总数
    │   └── DomainHealthCard                    ← 域健康评分
    ├── PropertyEditorPanel                     ← Step 3: 属性编辑器（标签页容器）
    │   ├── [Tab] EntityBasicInfoForm           ← 实体基本信息编辑
    │   │   ├── CodeField                       ← 编码（创建后不可改）
    │   │   ├── NameField                       ← 名称
    │   │   ├── EntityTypeSelector              ← 类型选择（MASTER/TRANSACTION/EVENT/REFERENCE）
    │   │   └── DescriptionField                ← 描述
    │   ├── [Tab] PropertyTableEditor           ← 属性表格（可编辑）
    │   │   ├── PropertyRow                     ← 单行属性
    │   │   │   ├── PropertyCodeInput           ← 属性编码
    │   │   │   ├── PropertyNameInput           ← 属性名称
    │   │   │   ├── PropertyTypeSelector        ← 类型选择
    │   │   │   ├── RequiredToggle              ← 必填开关
    │   │   │   ├── GlossaryTermRef (v1.1)      ← 引用术语库术语
    │   │   │   └── PropertyActions             ← 编辑/删除
    │   │   └── AddPropertyButton               ← 添加属性按钮
    │   ├── [Tab] RelationshipListPanel         ← 关系列表
    │   │   ├── RelationshipListItem            ← 单条关系
    │   │   │   ├── SourceLabel                  ← 源实体名
    │   │   │   ├── RelationshipTypeBadge        ← 关系类型标签
    │   │   │   ├── TargetLabel                  ← 目标实体名
    │   │   │   └── DeleteRelationshipButton     ← 删除关系
    │   │   └── NoRelationshipsEmpty             ← 空状态
    │   ├── [Tab] GlossaryBindingPanel (v1.1)   ← 术语关联面板
    │   │   ├── BoundTermsList                  ← 已绑定的术语列表
    │   │   │   ├── TermChip (名称+状态)        ← 术语芯片
    │   │   │   └── UnbindButton                ← 解绑按钮
    │   │   ├── AddTermButton                   ← 关联术语按钮
    │   │   └── TermSearchModal                 ← 术语搜索/选择弹窗
    │   │       ├── SearchInput                 ← 术语搜索
    │   │       ├── DomainFilter                ← 域筛选
    │   │       ├── StatusFilter                ← 状态筛选(仅PUBLISHED)
    │   │       └── TermSelectionList           ← 术语候选列表
    │   ├── [Tab] DataSourcePanel (v1.1)        ← 数据源标签页
    │   │   ├── PhysicalTableMapping            ← 物理表映射
    │   │   │   ├── BoundTableList              ← 已映射的物理表列表
    │   │   │   │   ├── TableChip               ← 表名+类型标签
    │   │   │   │   └── UnbindButton            ← 解绑映射
    │   │   │   ├── MapTableButton              ← 映射物理表按钮
    │   │   │   └── DataResourcePickerModal     ← 数据资源选择弹窗
    │   │   │       ├── SearchInput             ← 资源搜索
    │   │   │       ├── DatasourceFilter        ← 数据源筛选
    │   │   │       ├── TypeFilter              ← 类型筛选(TABLE/VIEW)
    │   │   │       └── ResourceGrid            ← 资源卡片网格
    │   │   ├── AutoDiscoverButton (v1.1)       ← 自动发现实体按钮
    │   │   ├── FieldMappingTable (v1.1)        ← 字段映射表
    │   │   │   ├── OntologyFieldColumn         ← 本体属性列
    │   │   │   ├── PhysicalFieldColumn         ← 物理字段列
    │   │   │   ├── MappingTypeSelector         ← 映射类型(自动/手动)
    │   │   │   └── SaveMappingButton           ← 保存映射
    │   │   ├── DataPreviewPanel (v1.1)         ← 实时数据预览
    │   │   │   ├── PreviewToolbar              ← 预览工具栏（刷新/导出）
    │   │   │   ├── PreviewDataTable            ← 数据行预览表格
    │   │   │   └── RowCountBadge               ← 行数统计
    │   │   └── SyncStatusBadge (v1.1)          ← 同步状态（已同步/待同步/未同步）
    │   └── ActionListPanel                     ← 动作列表（只读）
    │       └── ActionListItem                  ← 动作项
    └── EmptyStatePanel                         ← 未选中时的占位提示
```

### 5.2 共享组件（跨模块复用）

| 组件 | 用途 | 被哪些页面使用 |
|------|------|---------------|
| `EntityNode` | 画布上实体节点 | DomainGraphCanvas, KnowledgeGraphView |
| `RelationshipEdge` | 画布上关系边 | DomainGraphCanvas, KnowledgeGraphView |
| `EntityTypeIcon` | 实体类型图标 | EntityTreeNode, EntityNode, PropertyEditorPanel |
| `GraphCanvas` | 画布容器（zoom/pan/fit） | KnowledgeGraphView, DomainGraphCanvas |
| `PropertyTable` | 可编辑属性表格 | PropertyEditorPanel |
| `ConfirmDialog` | 删除确认 | 全模块 |
| `TermChip` | 术语芯片展示 (v1.1) | GlossaryBindingPanel, EntityNode |
| `TableChip` | 物理表芯片展示 (v1.1) | DataSourcePanel |

---

## 6. 数据流设计

### 6.1 顶层数据流架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     Data Flow Architecture                       │
│                                                                 │
│  Backend APIs (不变)                                             │
│  ┌──────────────────────────────────────────────────┐           │
│  │ GET /api/v1/ecos/knowledge-graph                 │           │
│  │ GET /api/v1/ecos/ontology/ont001/entities         │           │
│  │ POST/PUT/DELETE /api/v1/ecos/ontology/ont001/*    │           │
│  │ GET /api/glossary/terms          (v1.1 新增依赖)   │           │
│  │ GET /api/v1/datanet/metadata/*    (v1.1 新增依赖)  │           │
│  │ GET /api/v1/ecos/objects/*        (v1.1 新增依赖)  │           │
│  └──────────────┬───────────────────────────────────┘           │
│                 │                                                │
│                 ▼                                                │
│  ┌──────────────────────────────────────────────────┐           │
│  │            API Adapter Layer (新增)               │           │
│  │  · mapDomainFromKG()   ← 从 KG 数据提取域信息     │           │
│  │  · mapEntityFromAPI()  ← 统一实体数据格式          │           │
│  │  · mapGraphFromDomain()← 域实体→画布 nodes/edges   │           │
│  │  · mapGlossaryTerm()   ← 术语数据适配 (v1.1)       │           │
│  │  · mapDataResource()   ← 数据资源适配 (v1.1)       │           │
│  └──────────────┬───────────────────────────────────┘           │
│                 │                                                │
│                 ▼                                                │
│  ┌──────────────────────────────────────────────────┐           │
│  │           Zustand Store (新增)                    │           │
│  │  · useWorkbenchStore                             │           │
│  │    ├─ domains: Domain[]                           │           │
│  │    ├─ currentDomain: Domain | null                │           │
│  │    ├─ entities: Entity[]                          │           │
│  │    ├─ relationships: Relationship[]               │           │
│  │    ├─ selectedEntity: Entity | null               │           │
│  │    ├─ canvasNodes: Node[]                         │           │
│  │    ├─ canvasEdges: Edge[]                         │           │
│  │    ├─ glossaryTerms: GlossaryTerm[]  (v1.1)        │           │
│  │    ├─ entityBindings: EntityBinding[] (v1.1)       │           │
│  │    ├─ dataResources: DataResource[]  (v1.1)        │           │
│  │    ├─ fieldMappings: FieldMapping[]  (v1.1)        │           │
│  │    └─ actions: { fetch, create, update, delete }  │           │
│  └──────────────┬───────────────────────────────────┘           │
│                 │                                                │
│                 ▼                                                │
│  ┌──────────────────────────────────────────────────┐           │
│  │              React Components                     │           │
│  │  WorkbenchLeftPanel ←→ WorkbenchCanvas            │           │
│  │            ↕              ↕                        │           │
│  │       WorkbenchRightPanel                         │           │
│  └──────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 API Adapter Layer 设计

由于现有后端没有 Domain API，需要在前端通过 Adapter 层从现有数据中提取域信息：

```typescript
// adapters/domainAdapter.ts

/**
 * 从知识图谱数据中提取域信息
 * 策略：按 entity.domain 字段分组
 */
export function extractDomainsFromKG(kgData: KnowledgeGraphResponse): Domain[] {
  const domainMap = new Map<string, Domain>();

  kgData.nodes.forEach(node => {
    const domainCode = node.domain || 'default';
    if (!domainMap.has(domainCode)) {
      domainMap.set(domainCode, {
        code: domainCode,
        name: node.domainName || domainCode,
        entityCount: 0,
        relationshipCount: 0,
        entities: [],
        status: 'active',
      });
    }
    const domain = domainMap.get(domainCode)!;
    domain.entities.push(node.entityCode || node.id);
    domain.entityCount = domain.entities.length;
  });

  // 统计关系：判断边两端实体所属域
  kgData.edges.forEach(edge => {
    const sourceDomain = kgData.nodes.find(n => n.id === edge.source)?.domain;
    const targetDomain = kgData.nodes.find(n => n.id === edge.target)?.domain;
    if (sourceDomain && domainMap.has(sourceDomain)) {
      domainMap.get(sourceDomain)!.relationshipCount++;
    }
    if (targetDomain && targetDomain !== sourceDomain && domainMap.has(targetDomain)) {
      domainMap.get(targetDomain)!.relationshipCount++;
    }
  });

  return Array.from(domainMap.values());
}

/**
 * 将域内实体数据转换为 ReactFlow 画布格式
 */
export function mapEntitiesToFlow(entities: Entity[], relationships: Relationship[]): {
  nodes: Node[];
  edges: Edge[];
} {
  const nodes: Node[] = entities.map((entity, index) => ({
    id: entity.id,
    type: 'entityNode',
    position: calculateGridPosition(index, entities.length),
    data: {
      code: entity.code,
      name: entity.name,
      entityType: entity.entityType,
      propertyCount: entity.properties?.length || 0,
    },
  }));

  const edges: Edge[] = relationships.map(rel => ({
    id: rel.id,
    source: rel.sourceEntityId,
    target: rel.targetEntityId,
    type: 'relationshipEdge',
    data: {
      code: rel.code,
      name: rel.name,
      relationshipType: rel.relationshipType,
    },
  }));

  return { nodes, edges };
}
```

### 6.3 Zustand Store 设计

```typescript
// stores/useWorkbenchStore.ts
import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

interface WorkbenchState {
  // ===== 域状态 =====
  domains: Domain[];
  currentDomain: Domain | null;
  domainsLoading: boolean;

  // ===== 实体状态（当前域） =====
  entities: Entity[];
  entitiesLoading: boolean;

  // ===== 关系状态（当前域） =====
  relationships: Relationship[];
  relationshipsLoading: boolean;

  // ===== 画布状态 =====
  canvasNodes: Node[];
  canvasEdges: Edge[];

  // ===== 选中状态 =====
  selectedEntityId: string | null;
  selectedRelationshipId: string | null;

  // ===== 编辑状态 =====
  isEditing: boolean;
  editedEntity: Partial<Entity> | null;

  // ===== 全局图谱 =====
  kgData: KnowledgeGraphResponse | null;
  kgLoading: boolean;

  // ===== 术语库状态 (v1.1) =====
  glossaryTerms: GlossaryTerm[];
  glossaryTermsLoading: boolean;
  entityTermBindings: EntityTermBinding[];     // 实体→术语关联
  propertyTermBindings: PropertyTermBinding[]; // 属性→术语关联

  // ===== 数据映射状态 (v1.1) =====
  dataResources: DataResourceSummary[];         // 数据底座全部资源
  dataResourcesLoading: boolean;
  entityTableMappings: EntityTableMapping[];    // 实体→物理表映射
  fieldMappings: FieldMapping[];                // 属性→物理字段映射
  dataPreviewRows: Record<string, any>[];       // 实时数据预览
  dataPreviewLoading: boolean;

  // ===== Actions =====
  fetchKnowledgeGraph: () => Promise<void>;
  selectDomain: (domainCode: string) => Promise<void>;
  fetchDomainEntities: () => Promise<void>;
  fetchDomainRelationships: () => Promise<void>;

  // 画布操作
  addNode: (entity: Partial<Entity>) => void;
  updateNodePosition: (nodeId: string, position: XYPosition) => void;
  connectNodes: (source: string, target: string) => void;

  // 选中操作
  selectEntity: (entityId: string | null) => void;
  selectRelationship: (relId: string | null) => void;

  // CRUD
  createEntity: (data: CreateEntityDTO) => Promise<void>;
  updateEntity: (id: string, data: UpdateEntityDTO) => Promise<void>;
  deleteEntity: (id: string) => Promise<void>;
  createProperty: (entityId: string, data: CreatePropertyDTO) => Promise<void>;
  updateProperty: (entityId: string, propId: string, data: UpdatePropertyDTO) => Promise<void>;
  deleteProperty: (entityId: string, propId: string) => Promise<void>;
  createRelationship: (data: CreateRelationshipDTO) => Promise<void>;
  deleteRelationship: (relId: string) => Promise<void>;

  // 术语库操作 (v1.1)
  fetchGlossaryTerms: (filters?: GlossaryFilter) => Promise<void>;
  bindEntityToTerm: (entityId: string, termId: string) => Promise<void>;
  unbindEntityFromTerm: (entityId: string, termId: string) => Promise<void>;
  bindPropertyToTerm: (propertyId: string, termId: string) => Promise<void>;
  unbindPropertyFromTerm: (propertyId: string, termId: string) => Promise<void>;

  // 数据映射操作 (v1.1)
  fetchDataResources: () => Promise<void>;
  bindEntityToTable: (entityId: string, resourceId: string) => Promise<void>;
  unbindEntityFromTable: (entityId: string, resourceId: string) => Promise<void>;
  mapFieldToColumn: (mapping: CreateFieldMappingDTO) => Promise<void>;
  removeFieldMapping: (mappingId: string) => Promise<void>;
  fetchDataPreview: (entityId: string, limit?: number) => Promise<void>;
  autoDiscoverFromTable: (resourceId: string, domainCode: string) => Promise<void>;

  // 计算属性
  selectedEntity: () => Entity | null;
  selectedEntityProperties: () => Property[];
  selectedEntityRelationships: () => Relationship[];
  selectedEntityTerms: () => GlossaryTerm[];         // (v1.1)
  selectedEntityMappings: () => EntityTableMapping[]; // (v1.1)
}
```

### 6.4 数据流向图

```
┌──────────────────────────────────────────────────────────────┐
│  用户操作                                                     │
│                                                              │
│  点击域卡片 ─────────────────────────────────────┐            │
│  拖拽连线 ──────────────────────────────────┐    │            │
│  编辑属性 ────────────────────────────┐      │    │            │
│  关联术语 (v1.1) ────────────────┐    │      │    │            │
│  映射数据表 (v1.1) ──────────┐    │    │      │    │            │
│                              │    │    │      │    │            │
│                              ▼    ▼    ▼      ▼    ▼            │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              useWorkbenchStore (Zustand)                  │ │
│  │                                                          │ │
│  │  createEntity() ──→ POST /ontology/ont001/entities       │ │
│  │       │                                                  │ │
│  │       ├──→ entities[] ← append new entity                │ │
│  │       ├──→ canvasNodes[] ← add entity node               │ │
│  │       └──→ selectedEntityId ← new entity.id              │ │
│  │                                                          │ │
│  │  createRelationship() ──→ POST /ontology/ont001/relationships │
│  │       │                                                  │ │
│  │       ├──→ relationships[] ← append                      │ │
│  │       └──→ canvasEdges[] ← add relationship edge         │ │
│  │                                                          │ │
│  │  bindEntityToTerm() (v1.1) ──→ 写入 entityTermBindings[] │ │
│  │  bindEntityToTable() (v1.1) ──→ 写入 entityTableMappings[]│ │
│  / autoDiscoverFromTable() (v1.1) ──→ 创建实体+属性+映射     │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  组件订阅 (useWorkbenchStore selector)                    │ │
│  │                                                          │ │
│  │  EntityTreePanel ← entities, currentDomain               │ │
│  │  DomainGraphCanvas ← canvasNodes, canvasEdges            │ │
│  │  PropertyEditorPanel ← selectedEntity, properties        │ │
│  │  GlossaryBindingPanel ← entityTermBindings (v1.1)        │ │
│  │  DataSourcePanel ← entityTableMappings (v1.1)            │ │
│  └──────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. 核心页面布局设计

### 7.1 Step 1: 知识图谱首页 (`/ontology-workbench`)

```
┌──────────────────────────────────────────────────────────────┐
│  本体工作台                              [创建业务域]  [?帮助] │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                   ┌──────────────────────────┐               │
│                   │                          │               │
│                   │   全局知识图谱可视化      │   ┌─────────┐ │
│                   │   (GraphCanvas 只读)     │   │ 图谱统计 │ │
│                   │                          │   │         │ │
│                   │   ●──●──●               │   │ 实体:42  │ │
│                   │   │  │  │               │   │ 关系:87  │ │
│                   │   ●──●──●──●            │   │ 业务域:6 │ │
│                   │      │  │               │   │         │ │
│                   │      ●──●               │   │ [域列表] │ │
│                   │                          │   │ Sales   │ │
│                   │                          │   │ Supply  │ │
│                   └──────────────────────────┘   │ Finance │ │
│                                                  └─────────┘ │
│  业务域分类卡片（下方）                                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ 📦 销售域 │ │ 🏭 供应链 │ │ 💰 财务域 │ │ 👥 HR域  │ ...    │
│  │ 8实体·15关系│ │12实体·23关系│ │ 5实体·9关系│ │ 3实体·4关系│       │
│  │ [进入设计] │ │ [进入设计] │ │ [进入设计] │ │ [进入设计] │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Step 2: 域选择 (`/ontology-workbench/domains`)

```
┌──────────────────────────────────────────────────────────────┐
│  ← 返回图谱   业务域管理                    [+ 创建业务域]    │
├────────────────────┬─────────────────────────┬───────────────┤
│  域列表 (左侧)     │  域详情 (中间)          │  快速操作     │
│                    │                         │               │
│  🔍 [搜索域...]    │  ┌─────────────────┐   │  统计概览     │
│                    │  │                 │   │               │
│  📦 Sales    ●     │  │   Sales 销售域  │   │  实体: 8      │
│  🏭 SupplyChain   │  │   8 实体 · 15 关系│   │  关系: 15     │
│  💰 Finance       │  │                 │   │               │
│  👥 HR            │  │  [Customer]     │   │  ─────────    │
│  🔧 Engineering  │  │  [Order]        │   │               │
│  📊 Analytics    │  │  [Product]      │   │  [进入设计器] │
│                    │  │  [Contract]    │   │  [导出Schema] │
│                    │  │  ...           │   │               │
│                    │  └─────────────────┘   │               │
└────────────────────┴─────────────────────────┴───────────────┘
```

### 7.3 Step 3: 本体设计器 (`/ontology-workbench/domains/sales`)

```
┌──────────────────────────────────────────────────────────────┐
│  ← 域列表  Sales / 销售域                    [保存] [自动布局] │
├────────────┬──────────────────────────┬───────────────────────┤
│ 左侧 280px │    中间画布 (flex: 1)    │   右侧面板 320px      │
│            │                          │                       │
│ 当前域:    │  ┌──────────────────┐   │  ┌─────────────────┐  │
│ ┌────────┐ │  │ 工具栏:          │   │  │ 属性编辑器       │  │
│ │Sales ▼ │ │  │ [+实体] [布局]   │   │  │                 │  │
│ └────────┘ │  │ [🔍] [⊞] [⊟]    │   │  │ [基本信息]      │  │
│            │  └──────────────────┘   │  │ [属性列表]      │  │
│ 实体树     │                          │  │ [关联关系]      │  │
│ 🔍 [搜索] │     [Customer]─────┐     │  │ [术语关联] v1.1 │  │
│            │       │           │     │  │ [数据源]   v1.1 │  │
│ 📦 Customer│       │        [Order]  │  │                 │  │
│ 📋 Order   │   [Product]      │     │  │ 实体: Customer  │  │
│ 📦 Product │       │           │     │  │ 类型: MASTER    │  │
│ 📄 Contract│   [Contract]─────┘     │  │                 │  │
│            │                          │  │ ── 基本信息 ──  │  │
│  [+ 新建]  │  连线标签:              │  │ 编码: Customer  │  │
│            │  [PLACED] [OWNS]        │  │ 名称: 客户      │  │
│            │                          │  │ 描述: ...       │  │
│            │                          │  │                 │  │
│            │                          │  │ ── 属性列表 ──  │  │
│            │                          │  │ + name   STRING│  │
│            │                          │  │ + level  ENUM  │  │
│            │                          │  │ + phone  STRING│  │
│            │                          │  │ [+ 添加属性]   │  │
│            │                          │  └─────────────────┘  │
└────────────┴──────────────────────────┴───────────────────────┘
```

---

## 8. 后端API兼容策略

### 8.1 兼容承诺

> **零后端变更**：本次整合为纯前端工作，100% 使用现有后端端点，不新增、不修改任何后端 API。

### 8.2 API 使用映射

| 前端功能 | 现有 API | ontologyId |
|----------|----------|------------|
| 全局图谱总览 | `GET /api/v1/ecos/knowledge-graph` | — |
| 域下实体列表 | `GET /api/v1/ecos/ontology/ont001/entities` | 固定 `ont001` |
| 创建实体 | `POST /api/v1/ecos/ontology/ont001/entities` | 固定 `ont001` |
| 更新实体 | `PUT /api/v1/ecos/ontology/ont001/entities/{id}` | 固定 `ont001` |
| 删除实体 | `DELETE /api/v1/ecos/ontology/ont001/entities/{id}` | 固定 `ont001` |
| 属性 CRUD | `* /api/v1/ecos/ontology/ont001/entities/{id}/properties` | 固定 `ont001` |
| 关系 CRUD | `* /api/v1/ecos/ontology/ont001/relationships` | 固定 `ont001` |
| 术语列表查询 (v1.1) | `GET /api/glossary/terms` | — |
| 数据资源列表 (v1.1) | `GET /api/v1/datanet/metadata/resources/all` | — |
| 表字段查询 (v1.1) | `GET /api/v1/datanet/metadata/fields/{resourceId}` | — |
| 数据预览 (v1.1) | `GET /api/v1/datanet/metadata/preview/{resourceId}` | — |
| 对象Schema查询 (v1.1) | `GET /api/v1/ecos/objects/{entityCode}/schema` | — |

### 8.3 Domain 模拟策略

由于后端暂无 Domain API，前端通过以下策略模拟域概念：

```typescript
/**
 * Domain 数据来源策略（优先级从高到低）：
 *
 * 1. 从 knowledge-graph API 的 nodes 中提取 entity.domain 字段
 * 2. 如果没有 domain 字段，根据 entity.entityType 或命名前缀分组
 * 3. localStorage 缓存用户手动创建的域（仅前端存储）
 * 4. 【v1.1 新增】从术语库 GlossaryTerm.domain 字段中提取域名称（PUBLISHED 状态术语）
 *
 * 未来后端加入 Domain API 后（GET /api/v1/ecos/domains），
 * 只需替换 extractDomainsFromKG() 的实现即可平滑切换。
 */
```

### 8.4 API 客户端封装

```typescript
// services/ontologyApi.ts
const ONTOLOGY_ID = 'ont001'; // 当前固定 ontologyId
const BASE = `/api/v1/ecos`;

export const ontologyApi = {
  // === 知识图谱 ===
  fetchKnowledgeGraph: () =>
    apiClient.get<KnowledgeGraphResponse>(`${BASE}/knowledge-graph`),

  // === 实体 ===
  fetchEntities: () =>
    apiClient.get<Entity[]>(`${BASE}/ontology/${ONTOLOGY_ID}/entities`),

  createEntity: (data: CreateEntityDTO) =>
    apiClient.post<Entity>(`${BASE}/ontology/${ONTOLOGY_ID}/entities`, data),

  updateEntity: (id: string, data: UpdateEntityDTO) =>
    apiClient.put<Entity>(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${id}`, data),

  deleteEntity: (id: string) =>
    apiClient.delete(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${id}`),

  // === 属性 ===
  fetchProperties: (entityId: string) =>
    apiClient.get<Property[]>(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${entityId}/properties`),

  createProperty: (entityId: string, data: CreatePropertyDTO) =>
    apiClient.post<Property>(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${entityId}/properties`, data),

  updateProperty: (entityId: string, propId: string, data: UpdatePropertyDTO) =>
    apiClient.put<Property>(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${entityId}/properties/${propId}`, data),

  deleteProperty: (entityId: string, propId: string) =>
    apiClient.delete(`${BASE}/ontology/${ONTOLOGY_ID}/entities/${entityId}/properties/${propId}`),

  // === 关系 ===
  fetchRelationships: () =>
    apiClient.get<Relationship[]>(`${BASE}/ontology/${ONTOLOGY_ID}/relationships`),

  createRelationship: (data: CreateRelationshipDTO) =>
    apiClient.post<Relationship>(`${BASE}/ontology/${ONTOLOGY_ID}/relationships`, data),

  deleteRelationship: (relId: string) =>
    apiClient.delete(`${BASE}/ontology/${ONTOLOGY_ID}/relationships/${relId}`),
};

// ========== v1.1 新增 API 客户端 (术语库 + 数据底座) ==========

// services/glossaryClient.ts
export const glossaryClient = {
  fetchTerms: (filters?: GlossaryFilter) =>
    apiClient.get<GlossaryListResult>('/api/glossary/terms', { params: filters }),
};

// services/dataCatalogClient.ts
export const dataCatalogClient = {
  fetchAllResources: () =>
    apiClient.get<BulkResource[]>('/api/v1/datanet/metadata/resources/all'),

  fetchFields: (resourceId: string) =>
    apiClient.get<DataField[]>('/api/v1/datanet/metadata/fields/' + resourceId),

  fetchPreview: (resourceId: string, limit?: number) =>
    apiClient.get<PreviewResult>('/api/v1/datanet/metadata/preview/' + resourceId, { params: { limit } }),

  fetchObjectSchema: (entityCode: string) =>
    apiClient.get<SchemaResult>('/api/v1/ecos/objects/' + entityCode + '/schema'),
};
```

---

## 9. 状态管理设计

### 9.1 Zustand Store 完整接口

```typescript
// stores/useWorkbenchStore.ts

interface WorkbenchStore {
  // ─── 数据 ───
  kgData: KnowledgeGraphResponse | null;
  domains: Domain[];
  currentDomainCode: string | null;
  entities: Entity[];
  relationships: Relationship[];
  properties: Record<string, Property[]>; // entityId → properties

  // ─── 画布 ───
  canvasNodes: Node<EntityNodeData>[];
  canvasEdges: Edge<RelationshipEdgeData>[];

  // ─── 选中 ───
  selectedEntityId: string | null;

  // ─── UI 状态 ───
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;
  canvasViewport: { x: number; y: number; zoom: number };

  // ─── 加载态 ───
  kgLoading: boolean;
  entitiesLoading: boolean;
  relationshipsLoading: boolean;
  savingEntity: boolean;
  error: string | null;

  // ─── 术语库状态 (v1.1) ───
  glossaryTerms: GlossaryTerm[];
  glossaryTermsLoading: boolean;
  entityTermBindings: Record<string, string[]>;   // entityId → termId[]
  propertyTermBindings: Record<string, string>;    // propertyId → termId

  // ─── 数据映射状态 (v1.1) ───
  dataResources: BulkResource[];
  dataResourcesLoading: boolean;
  entityTableMappings: Record<string, string[]>;   // entityId → resourceId[]
  fieldMappings: Record<string, FieldMapping[]>;    // entityId → FieldMapping[]
  dataPreview: { rows: any[]; loading: boolean };   // 当前选中实体的数据预览

  // ─── 计算属性 (selectors) ───
  currentDomain: () => Domain | undefined;
  selectedEntity: () => Entity | undefined;
  selectedEntityProperties: () => Property[];
  selectedEntityRelationships: () => Relationship[];
  selectedEntityTerms: () => GlossaryTerm[];
  selectedEntityTableMappings: () => { resource: BulkResource; fields: DataField[] }[];

  // ─── Actions ───
  fetchKGAndDomains: () => Promise<void>;
  setCurrentDomain: (code: string) => void;
  fetchDomainData: () => Promise<void>;
  syncCanvasFromData: () => void;

  selectEntity: (id: string | null) => void;
  setError: (error: string | null) => void;
  toggleLeftPanel: () => void;
  toggleRightPanel: () => void;

  // CRUD
  createEntity: (data: CreateEntityDTO) => Promise<Entity>;
  updateEntity: (id: string, data: UpdateEntityDTO) => Promise<void>;
  deleteEntity: (id: string) => Promise<void>;
  createProperty: (entityId: string, data: CreatePropertyDTO) => Promise<void>;
  updateProperty: (entityId: string, propId: string, data: UpdatePropertyDTO) => Promise<void>;
  deleteProperty: (entityId: string, propId: string) => Promise<void>;
  createRelationship: (data: CreateRelationshipDTO) => Promise<void>;
  deleteRelationship: (relId: string) => Promise<void>;

  // 画布交互
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  onConnect: OnConnect;

  // 术语库操作 (v1.1)
  fetchGlossaryTerms: (filters?: { domain?: string; status?: string }) => Promise<void>;
  bindEntityToTerm: (entityId: string, termId: string) => void;
  unbindEntityFromTerm: (entityId: string, termId: string) => void;
  bindPropertyToTerm: (propertyId: string, termId: string) => void;
  unbindPropertyFromTerm: (propertyId: string) => void;

  // 数据映射操作 (v1.1)
  fetchDataResources: () => Promise<void>;
  bindEntityToTable: (entityId: string, resourceId: string) => Promise<void>;
  unbindEntityFromTable: (entityId: string, resourceId: string) => void;
  saveFieldMappings: (entityId: string, mappings: FieldMapping[]) => void;
  fetchDataPreview: (entityId: string) => Promise<void>;
  autoDiscoverFromTable: (resourceId: string, domainCode: string) => Promise<Entity>;
}
```

### 9.2 Store 数据同步策略

```
用户操作                       Store 更新                         API 调用
────────                     ──────────                        ────────

创建实体                     1. entities.push(newEntity)       POST /ontology/
                             2. canvasNodes.push(node)          ont001/entities
                             3. selectedEntityId = newId

创建关系(连线)                1. relationships.push(newRel)     POST /ontology/
                             2. canvasEdges.push(edge)          ont001/relationships

删除实体                     1. entities = filter(id)          DELETE /ontology/
                             2. canvasNodes = filter(id)        ont001/entities/{id}
                             3. relationships = filter(rel)
                             4. canvasEdges = filter(edge)

编辑属性                     1. properties[id][idx] = updated   PUT /ontology/
                             2. selectedEntity 刷新              ont001/entities/
                                                                {id}/properties/{pid}

(v1.1) 关联术语              1. entityTermBindings[id].push()   前端本地存储
                             2. 画布节点显示 GlossaryBadge       (localStorage 持久化)

(v1.1) 映射物理表            1. entityTableMappings[id].push()  GET /datanet/metadata/
                             2. fetchFields(resourceId)          fields/{resourceId}
                             3. fieldMappings[id] = mappings

(v1.1) 自动发现              1. createEntity(data)              POST /ontology/ont001/
                             2. createProperty * N               entities
                             3. bindEntityToTable                POST .../properties
                             4. saveFieldMappings                前端本地存储
```

---

## 10. 关键交互流程

### 10.1 主工作流（完整用户旅程）

```
用户进入 /ontology-workbench
  │
  ├─ Step 1: 知识图谱首页
  │   ├─ 加载 GET /api/v1/ecos/knowledge-graph
  │   ├─ 显示全局图谱（只读 GraphCanvas）
  │   ├─ 从 KG 数据提取域信息 → 生成域卡片列表
  │   └─ 用户点击某个域卡片 [进入设计]
  │
  ├─ Step 2: 域详情
  │   └─ 路由跳转 → /ontology-workbench/domains/sales
  │       ├─ 加载 GET /ontology/ont001/entities (当前域实体)
  │       ├─ 加载 GET /ontology/ont001/relationships
  │       ├─ 左侧显示域内实体树
  │       ├─ 中间显示域内实体节点图
  │       └─ 右侧显示域统计
  │
  └─ Step 3: 开始设计
      ├─ 双击画布空白处 → 弹出 "新建实体" 对话框
      │   ├─ 填写 entity code/name/type
      │   ├─ POST /ontology/ont001/entities
      │   └─ 画布自动添加新节点
      │
      ├─ 单击实体节点 → 右侧面板显示实体详情
      │   ├─ 基本信息编辑
      │   ├─ 属性表格（添加/编辑/删除属性）
      │   ├─ 关联关系列表
      │   ├─ [v1.1] 术语关联标签页
      │   └─ [v1.1] 数据源标签页
      │
      ├─ 拖拽连线创建关系
      │   ├─ 从源实体拖出连接线 → 连到目标实体
      │   ├─ 弹出关系配置对话框
      │   │   ├─ 选择关系类型 (ONE_TO_ONE / ONE_TO_MANY / MANY_TO_MANY)
      │   │   ├─ 填写关系 code/name
      │   │   └─ 配置基数
      │   ├─ POST /ontology/ont001/relationships
      │   └─ 画布显示新连线
      │
      └─ 拖拽节点调整布局 → 自动保存位置到 localStorage
```

### 10.2 实体创建交互流程

```
用户点击画布 [+实体] 按钮
  │
  ▼
弹出 CreateEntityModal
  │
  ├─ 填写表单
  │   ├─ code (必填, 英文标识)
  │   ├─ name (必填, 中文名称)
  │   ├─ entityType (下拉: MASTER/TRANSACTION/EVENT/REFERENCE)
  │   └─ description (可选)
  │
  ├─ 点击 [创建]
  │   │
  │   ├─ POST /api/v1/ecos/ontology/ont001/entities
  │   │   Body: { code, name, entityType, description }
  │   │
  │   ├─ 成功 → Response: { id, code, name, ... }
  │   │   ├─ Store: entities.push(newEntity)
  │   │   ├─ Store: canvasNodes.push(newNode)
  │   │   ├─ Store: selectedEntityId = newEntity.id
  │   │   ├─ Canvas: 新节点出现在画布中心
  │   │   ├─ Right Panel: 显示新实体属性编辑器
  │   │   └─ Left Panel: 实体树新增节点
  │   │
  │   └─ 失败 → 显示错误 toast
  │
  └─ 点击 [取消] → 关闭弹窗
```

### 10.3 关系连线创建流程

```
用户在画布上拖拽连线
  │
  ├─ 从 source 实体节点的连接点 (handle) 拖出
  │   │
  │   ├─ 画布显示临时连线（虚线，跟随鼠标）
  │   └─ 有效的 target handle 高亮显示
  │
  ├─ 松手到 target 实体节点的 handle
  │   │
  │   ├─ 如果 source === target → 提示不能自连接
  │   │
  │   └─ 弹出 CreateRelationshipModal
  │       ├─ 自动填充 sourceEntityId, targetEntityId
  │       ├─ 用户填写:
  │       │   ├─ code (关系编码)
  │       │   ├─ name (关系名称)
  │       │   ├─ relationshipType (ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY)
  │       │   └─ 基数配置 (可选)
  │       │
  │       ├─ 点击 [创建]
  │       │   ├─ POST /ontology/ont001/relationships
  │       │   ├─ 成功 → canvasEdges.push(newEdge)
  │       │   └─ 失败 → 错误提示
  │       │
  │       └─ 点击 [取消] → 移除临时连线
  │
  └─ 拖拽到空白区域 → 不触发任何操作
```

### 10.4 实体选中与属性编辑流程

```
用户点击画布上的实体节点 或 左侧实体树中的节点
  │
  ▼
Store: selectedEntityId = entityId
  │
  ├─ 画布: 节点高亮 + 边框加粗 + 相邻关系高亮
  ├─ 左侧: 实体树对应节点高亮
  │
  └─ 右侧面板: PropertyEditorPanel 加载
      │
      ├─ 加载实体详情 (已缓存)
      │
      ├─ 加载属性列表
      │   ├─ 如果已缓存 → 直接显示
      │   └─ 如果未缓存 → GET /ontology/ont001/entities/{id}/properties
      │
      ├─ 加载关联关系
      │   └─ 从 relationships[] 中过滤 sourceEntityId or targetEntityId === entityId
      │
      ├─ [v1.1] 加载关联术语
      │   └─ 从 entityTermBindings[id] 中获取 termId[] → 匹配 glossaryTerms
      │
      └─ 用户编辑属性
          ├─ 点击 [+ 添加属性]
          │   ├─ 新增一行 PropertyRow
          │   ├─ 填写 code/name/type/required
          │   └─ 点击保存 → POST .../properties
          │
          ├─ 行内编辑已有属性
          │   └─ 失焦自动保存 → PUT .../properties/{pid}
          │
          └─ 删除属性
              ├─ 确认对话框
              └─ DELETE .../properties/{pid}
```

### 10.5 实体拖拽与布局保存

```
用户拖拽画布实体节点
  │
  ▼
ReactFlow onNodesChange 事件
  │
  ├─ Store: canvasNodes[].position 更新
  │
  └─ 防抖 500ms 后保存到 localStorage
      │
      └─ key: `workbench-layout-{domainCode}`
          value: { [nodeId]: { x, y } }
```

---

## 11. 术语库集成

> **版本**: v1.1 新增  
> **背景**: 现有术语库模块 (`GlossaryManager`, `/api/glossary/terms`) 管理业务术语的全生命周期。术语定义了业务概念的标准名称、定义、所属域及状态（`DRAFT → REVIEW → PUBLISHED → DEPRECATED`）。将术语库能力集成到本体工作台，可以确保本体设计中的实体和属性与已标准化定义的业务术语对齐，提升语义一致性。

### 11.1 设计目标

| 目标 | 说明 |
|------|------|
| **语义对齐** | 本体实体/属性可以关联术语库中的标准术语，确保命名和定义的一致性 |
| **双向追溯** | 从术语可找到引用了它的实体/属性；从实体/属性可看到所引用的术语定义 |
| **域自动生成** | 从术语库 PUBLISHED 状态术语的 `domain` 字段自动生成/校验本体业务域 |
| **属性语义标注** | 属性定义时可引用术语库术语，作为属性的业务语义描述 |
| **非侵入式** | 术语关联为可选功能，不强制关联，不影响现有本体数据 |

### 11.2 术语库数据模型 (GlossaryTerm)

```typescript
// 来自 /api/glossary/terms
interface GlossaryTerm {
  id: string;
  code: string | null;         // 术语编码（系统生成）
  name: string;                 // 术语名称（核心字段）
  definition: string;           // 术语定义
  domain: string;               // 所属业务域：数据管理 | AI技术 | 业务术语 | 技术架构 | 安全合规 | 其他
  owner: string | null;         // 负责人
  status: string;               // DRAFT → REVIEW → PUBLISHED → DEPRECATED
  createdBy: string | null;
  createdAt: string;
  updatedAt?: string;
}
```

内置域列表：`["数据管理", "AI技术", "业务术语", "技术架构", "安全合规", "其他"]`

### 11.3 术语关联模型设计

```typescript
/**
 * 实体级术语关联
 * 一个本体实体可以关联 0..N 个术语
 * 关联数据仅前端存储（localStorage），不涉及后端新增 API
 */
interface EntityTermBinding {
  entityId: string;            // 本体实体 ID
  termId: string;              // 术语 ID
  boundAt: string;             // 关联时间（ISO 8601）
  boundBy: string;             // 操作用户
}

/**
 * 属性级术语引用
 * 一个本体属性可以引用 0..1 个术语（通常用于取值来自某个业务概念）
 */
interface PropertyTermBinding {
  propertyId: string;          // 本体属性 ID
  termId: string;              // 术语 ID
  boundAt: string;
  boundBy: string;
}

// 前端 localStorage 存储 KEY
//   entity-bindings    → Record<string, string[]>  (entityId → termId[])
//   property-bindings  → Record<string, string>     (propertyId → termId)
```

### 11.4 核心交互设计

#### 11.4.1 实体关联术语

```
用户在右侧面板选中实体 → 切换到 [术语关联] 标签页
  │
  ├─ 显示当前实体已关联的术语列表
  │   └─ 每条术语显示: 名称 Chip + 状态标签 + 定义 Tooltip + 解绑按钮
  │
  └─ 点击 [关联术语] 按钮
      │
      ▼
弹出 TermSearchModal（术语搜索/选择弹窗）
  │
  ├─ 默认筛选: status=PUBLISHED（仅已发布的术语可被引用）
  ├─ 可选筛选: 按 domain 过滤（方便按域检索）
  ├─ 搜索: 输入术语名称关键词实时搜索
  ├─ 术语来源: GET /api/glossary/terms?status=PUBLISHED
  │
  ├─ 用户勾选一个或多个术语
  │
  └─ 点击 [确认关联]
      ├─ Store: entityTermBindings[id] = [...termIds]
      ├─ 画布: EntityNode 显示 GlossaryBadge（📖 已关联 N 个术语）
      ├─ 右侧面板刷新已关联列表
      └─ localStorage 持久化绑定关系
```

#### 11.4.2 属性引用术语

```
用户编辑属性时
  │
  ├─ PropertyRow 增加 "术语引用" 字段（可选下拉）
  │   ├─ 下拉列表来源: GET /api/glossary/terms?status=PUBLISHED
  │   ├─ 选中某个术语后，属性的 businessDescription 自动填充术语定义
  │   └─ 属性的 code/name 保持独立编辑，不强制与术语 name 一致
  │
  └─ 效果:
      ├─ 属性语义标注: 属性的业务含义由关联的 PUBLISHED 术语定义
      └─ 同一术语可被多个属性/实体引用（多对多关系不限制）
```

#### 11.4.3 域自动生成（从术语库 domain 字段）

```typescript
/**
 * 术语库中的术语按 domain 字段分布，可作为本体域的候选来源
 * 策略:
 *   1. 获取所有 PUBLISHED 状态的术语
 *   2. 按 domain 分组统计术语数量
 *   3. 将高频 domain 作为业务域候选
 *   4. 与从 KG 提取的域进行合并/去重
 */
export function extractDomainsFromGlossary(terms: GlossaryTerm[]): Domain[] {
  const domainMap = new Map<string, { count: number; terms: string[] }>();

  terms
    .filter(t => t.status === 'PUBLISHED' && t.domain)
    .forEach(t => {
      if (!domainMap.has(t.domain)) {
        domainMap.set(t.domain, { count: 0, terms: [] });
      }
      const entry = domainMap.get(t.domain)!;
      entry.count++;
      entry.terms.push(t.name);
    });

  return Array.from(domainMap.entries()).map(([code, info]) => ({
    code,
    name: code,
    entityCount: 0,
    relationshipCount: 0,
    entities: [],
    status: 'active' as const,
    source: 'glossary',              // 标记来源
    termCount: info.count,
    sampleTerms: info.terms.slice(0, 5), // 采样术语名
  }));
}
```

### 11.5 右侧面板扩展

在 Step 3 设计器的右侧属性面板中，原有的三个区域扩展为**标签页式布局**：

```
┌─────────────────────────────────────┐
│  PropertyEditorPanel                │
│                                     │
│  ┌─ [基本信息] [属性] [关系] ─────┐ │
│  │  [术语关联] [数据源] [动作]     │ │  ← v1.1 新增两个标签页
│  └────────────────────────────────┘ │
│                                     │
│  📖 术语关联 (当前激活)             │
│  ┌─────────────────────────────────┐│
│  │  已关联术语                     ││
│  │  ┌──────────────────────────┐  ││
│  │  │ 🔖 客户主数据   PUBLISHED │  ││
│  │  │   企业核心业务实体...      │  ││
│  │  │                  [解绑]  │  ││
│  │  └──────────────────────────┘  ││
│  │  ┌──────────────────────────┐  ││
│  │  │ 🔖 客户分级     REVIEW    │  ││
│  │  │   基于交易额和忠诚度...   │  ││
│  │  │                  [解绑]  │  ││
│  │  └──────────────────────────┘  ││
│  │                                 ││
│  │  [+ 关联术语]                   ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

### 11.6 术语库集成 API 依赖

| API | 用途 | 调用时机 |
|-----|------|---------|
| `GET /api/glossary/terms?status=PUBLISHED` | 获取可选术语列表 | 打开 TermSearchModal |
| `GET /api/glossary/terms?domain=X` | 按域筛选术语 | 用户选择域过滤条件 |
| `GET /api/glossary/terms` | 全量术语（用于域提取） | 首次加载工作台时 |

> **注意**: 术语关联数据（entityTermBindings / propertyTermBindings）为**前端本地存储**，不通过现有 API 写入后端。这将作为未来后端 `entity.termRefs` 字段的前置实现。

---

## 12. 数据映射与数据同步

> **版本**: v1.1 新增  
> **背景**: 现有数据浏览器 (`ObjectExplorer`) 可从数据底座 (`DataCatalog` / `DatasetExplorer`) 浏览实际业务数据（供应商、客户、合同等物理表数据）。数据底座通过 `/api/v1/datanet/metadata/` 提供表/视图元数据、字段列表和数据预览。将数据底座能力集成到本体工作台，可以打通「业务语义 → 本体设计 → 物理数据」三层，实现本体驱动的数据可观测性。

### 12.1 设计目标

| 目标 | 说明 |
|------|------|
| **语义到物理的映射** | 为本体实体建立到物理表/视图的映射关系，打通业务概念与数据存储 |
| **自动发现** | 从数据底座自动扫描物理表结构，一键生成本体实体及属性 |
| **字段级映射** | 本体属性可映射到物理表/视图的具体字段，建立精确的数据溯源 |
| **实时数据预览** | 在右侧面板中直接预览该实体映射的物理表最新数据行 |
| **非侵入式** | 数据映射为可选增强功能，不影响现有本体设计和数据底座 |

### 12.2 数据底座模型

```typescript
// 物理数据资源（来自 /api/v1/datanet/metadata/resources/all）
interface BulkResource {
  resourceId: string;              // 资源ID
  resourceName: string;            // 资源名称（表名/视图名）
  resourceType: string;            // 类型: "TABLE" | "VIEW"
  sourcePath: string;              // 源路径（如 "sales.customer"）
  datasourceName: string;          // 数据源名称
  datasourceType: string;          // 数据源类型: "JDBC" | ...
  fieldCount: number;              // 字段数量
}

// 物理字段（来自 /api/v1/datanet/metadata/fields/{resourceId}）
interface DataField {
  fieldName: string;               // 字段名
  fieldType: string;               // 字段类型: VARCHAR/INTEGER/DECIMAL/DATE/...
  nullable: boolean;               // 是否可为空
  primaryKey: boolean;             // 是否主键
  comment: string;                  // 字段注释
}

// 数据预览行（来自 /api/v1/datanet/metadata/preview/{resourceId}）
// 返回格式: { rows: Record<string, any>[], columns: number, rowCount: number }
```

### 12.3 数据映射模型设计

```typescript
/**
 * 实体 ↔ 物理表映射
 * 一个本体实体可以映射到 0..N 个物理表/视图
 */
interface EntityTableMapping {
  id: string;                     // 映射ID
  entityId: string;               // 本体实体 ID
  resourceId: string;             // 物理资源 ID
  resourceName: string;           // 物理资源名称（冗余，方便展示）
  resourceType: string;           // TABLE | VIEW
  datasourceName: string;         // 数据源名称
  mappingType: 'manual' | 'auto_discovered';  // 手动映射 / 自动发现
  mappedAt: string;               // 映射时间
  mappedBy: string;               // 操作用户
}

/**
 * 属性 ↔ 物理字段映射
 * 本体属性映射到物理表的具体字段
 */
interface FieldMapping {
  id: string;                       // 映射ID
  entityId: string;                 // 本体实体 ID
  propertyCode: string;             // 本体属性 code
  resourceId: string;               // 物理资源 ID
  fieldName: string;                // 物理字段名
  fieldType: string;                // 物理字段类型
  mappingType: 'auto' | 'manual';   // 自动推断 / 手动指定
  confidence?: number;              // 自动匹配置信度 (0-1)
}
```

### 12.4 核心交互设计

#### 12.4.1 手动映射物理表

```
用户在右侧面板选中实体 → 切换到 [数据源] 标签页
  │
  ├─ 显示当前实体已映射的物理表列表
  │   └─ 每个映射显示: 表名 Chip + 类型(TABLE/VIEW) + 数据源 + 解绑按钮
  │
  └─ 点击 [映射物理表] 按钮
      │
      ▼
弹出 DataResourcePickerModal（数据资源选择弹窗）
  │
  ├─ 加载数据底座全部资源: GET /api/v1/datanet/metadata/resources/all
  ├─ 搜索: 输入表名关键词实时过滤
  ├─ 筛选: 按数据源(datasourceName) / 类型(TABLE/VIEW) 过滤
  ├─ 展示: 资源卡片网格（名称 / 类型 / 数据源 / 字段数 / 行数）
  │
  ├─ 用户选择一个或多个物理表
  │
  └─ 点击 [确认映射]
      ├─ Store: entityTableMappings[id].push(mapping)
      ├─ 进一步: 加载字段列表 GET /datanet/metadata/fields/{resourceId}
      ├─ 自动字段匹配:
      │   ├─ 如果本体属性和物理字段 code/name 同名 → 自动映射 (confidence=1.0)
      │   ├─ 如果名称相似（编辑距离 < 3）→ 建议映射 (confidence=0.5~0.9)
      │   └─ 其余 → 用户手动指定
      ├─ 显示 FieldMappingTable（字段映射表）
      └─ localStorage 持久化映射关系
```

#### 12.4.2 字段映射表 (FieldMappingTable)

```
┌──────────────────────────────────────────────────────┐
│  字段映射: Customer ↔ sales.customers (TABLE)        │
│                                                      │
│  ┌─────────────┬──────────────┬──────────┬────────┐ │
│  │ 本体属性     │ 物理字段      │ 类型     │ 操作   │ │
│  ├─────────────┼──────────────┼──────────┼────────┤ │
│  │ name        │ cust_name    │ ⚡ 自动  │ [✕]   │ │
│  │ phone       │ phone_num    │ ⚡ 自动  │ [✕]   │ │
│  │ level       │ —            │ 未映射   │ [映射] │ │
│  │ —           │ created_at   │ 未映射   │ [忽略] │ │
│  └─────────────┴──────────────┴──────────┴────────┘ │
│                                                      │
│  [保存映射]  [重新自动匹配]                           │
└──────────────────────────────────────────────────────┘
```

#### 12.4.3 自动发现（从物理表生成实体）

这是最核心的新增能力：从数据底座选择一张物理表，一键生成对应的本体实体 + 属性 + 映射关系。

```
用户在画布工具栏点击 [自动发现] 或右侧面板点击 [从数据表创建实体]
  │
  ▼
弹出 DataResourcePickerModal
  │
  ├─ 用户选择一张物理表（如 "sales.customers"）
  │
  └─ 点击 [生成本体实体]
      │
      ▼
系统执行以下步骤:
  │
  ├─ Step 1: 获取表元数据
  │   ├─ GET /datanet/metadata/fields/{resourceId} → 字段列表
  │   └─ GET /datanet/metadata/preview/{resourceId} → 样例数据
  │
  ├─ Step 2: 类型映射 (物理类型 → 本体属性类型)
  │   ├─ VARCHAR / TEXT / CHAR → STRING
  │   ├─ INTEGER / BIGINT → NUMBER
  │   ├─ DECIMAL / FLOAT / DOUBLE → NUMBER
  │   ├─ DATE / DATETIME / TIMESTAMP → DATE
  │   ├─ BOOLEAN / BIT → BOOLEAN
  │   └─ 其他 → STRING (fallback)
  │
  ├─ Step 3: 创建本体实体
  │   ├─ entityCode = 表名转驼峰 (如 "customers" → "Customer")
  │   ├─ entityName = 表名或注释
  │   ├─ entityType = MASTER（默认，可后续修改）
  │   ├─ domain = 当前选中的域
  │   ├─ POST /ontology/ont001/entities
  │   └─ 获取新实体 ID
  │
  ├─ Step 4: 创建属性（基于字段）
  │   ├─ 遍历字段列表
  │   ├─ propertyCode = 字段名转驼峰 (如 "cust_name" → "custName")
  │   ├─ propertyName = 字段注释 || 字段名
  │   ├─ propertyType = 步骤2映射后的类型
  │   ├─ required = !nullable 且 !primaryKey
  │   ├─ POST /ontology/ont001/entities/{id}/properties (逐条或批量)
  │   └─ 主键字段: required=true , 标注为标识属性
  │
  ├─ Step 5: 自动建立映射
  │   ├─ 创建 EntityTableMapping (entityId ↔ resourceId)
  │   └─ 创建 FieldMapping[] (每个属性 ↔ 对应字段, mappingType='auto', confidence=1.0)
  │
  └─ Step 6: 刷新工作台
      ├─ Store: entities.push(newEntity)
      ├─ Store: canvasNodes.push(newNode)
      ├─ Store: 选中新实体
      ├─ Canvas: 新节点出现在画布
      └─ Right Panel: 显示新实体详情 + 字段映射表

// 类型映射函数
function mapPhysicalTypeToOntologyType(physicalType: string): string {
  const upper = physicalType.toUpperCase();
  if (/VARCHAR|CHAR|TEXT|CLOB|NVARCHAR/i.test(upper)) return 'STRING';
  if (/INT|BIGINT|SMALLINT|TINYINT|NUMBER/i.test(upper)) return 'NUMBER';
  if (/DECIMAL|FLOAT|DOUBLE|NUMERIC/i.test(upper)) return 'NUMBER';
  if (/DATE|TIME|TIMESTAMP|DATETIME/i.test(upper)) return 'DATE';
  if (/BOOL/i.test(upper)) return 'BOOLEAN';
  if (/JSON|ARRAY/i.test(upper)) return 'OBJECT';
  return 'STRING';
}
```

#### 12.4.4 实时数据预览

```
用户在 [数据源] 标签页中点击某个物理表的 [预览数据] 按钮
  │
  ▼
右侧面板展开 DataPreviewPanel
  │
  ├─ 调用: GET /datanet/metadata/preview/{resourceId}?limit=50
  ├─ 显示:
  │   ├─ 预览工具栏: [刷新] 按钮 (30s自动刷新可配置)
  │   ├─ 数据行数统计: "显示前 50 行 / 共 123,456 行"
  │   │
  │   └─ 数据预览表格:
  │       ┌──────┬────────────┬──────────┬───────┐
  │       │ id   │ cust_name  │ phone    │ level │
  │       ├──────┼────────────┼──────────┼───────┤
  │       │ 1001 │ 中科软     │ 138...   │ VIP   │
  │       │ 1002 │ 恒生电子   │ 139...   │ A级   │
  │       │ ...  │ ...        │ ...      │ ...   │
  │       └──────┴────────────┴──────────┴───────┘
  │
  └─ 状态显示: SyncStatusBadge
      ├─ 🟢 已同步: 映射已建立，与物理表字段一致
      ├─ 🟡 待同步: 映射已建立，但本体属性与物理字段有差异
      └─ ⚪ 未映射: 尚未建立映射关系
```

### 12.5 右侧面板扩展

```
┌─────────────────────────────────────┐
│  PropertyEditorPanel                │
│                                     │
│  ┌─────────────── 标签页 ──────────┐│
│  │ [基本信息] [属性] [关系]         ││
│  │ [术语关联] [数据源] [动作]       ││
│  └─────────────────────────────────┘│
│                                     │
│  🗄 数据源 (当前激活)               │
│  ┌─────────────────────────────────┐│
│  │  状态: 🟢 已同步                ││
│  │                                 ││
│  │  已映射物理表                    ││
│  │  ┌──────────────────────────┐  ││
│  │  │ 🗃 sales.customers TABLE │  ││
│  │  │   数据源: prod_mysql     │  ││
│  │  │   字段数: 15 · 行数:12万│  ││
│  │  │   [预览] [解绑]         │  ││
│  │  └──────────────────────────┘  ││
│  │                                 ││
│  │  [+ 映射物理表]  [自动发现]     ││
│  │                                 ││
│  │  ── 字段映射 ──                ││
│  │  name → cust_name    ⚡自动     ││
│  │  phone → phone_num   ⚡自动     ││
│  │  level → (未映射)              ││
│  │                                 ││
│  │  ── 数据预览 ──                ││
│  │  显示前 50 行 / 共 123,456 行  ││
│  │  ┌──────┬──────┬───────┐      ││
│  │  │ id   │ name │ level │      ││
│  │  │ 1001 │ 中科│ VIP   │      ││
│  │  │ 1002 │ 恒生│ A级   │      ││
│  │  └──────┴──────┴───────┘      ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

### 12.6 数据映射 API 依赖

| API | 用途 | 调用时机 |
|-----|------|---------|
| `GET /api/v1/datanet/metadata/resources/all` | 获取全部数据资源列表 | 打开 DataResourcePickerModal / 首次加载 |
| `GET /api/v1/datanet/metadata/fields/{resourceId}` | 获取物理表字段列表 | 建立映射后 / 自动发现时 |
| `GET /api/v1/datanet/metadata/preview/{resourceId}?limit=50` | 获取数据预览 | 用户点击 [预览] 按钮 |
| `POST /api/v1/ecos/ontology/ont001/entities` | 创建实体 | 自动发现时 |
| `POST /api/v1/ecos/ontology/ont001/entities/{id}/properties` | 创建属性 | 自动发现时（逐字段创建） |

> **注意**: 实体-表映射关系（EntityTableMapping）和字段级映射（FieldMapping）为**前端本地存储**，通过 localStorage 持久化。这作为未来后端 `entity.physicalTableRefs` / `property.physicalFieldRef` 字段的前置实现。

---

## 13. 组件规格详表

### 13.1 页面级组件

| 组件名 | 文件路径 | Props | 状态依赖 | 说明 |
|--------|---------|-------|---------|------|
| `OntologyWorkbenchLayout` | `pages/OntologyWorkbenchLayout.tsx` | — | currentDomain | 三栏布局骨架，根据路由切换子组件 |
| `KnowledgeGraphHome` | `pages/KnowledgeGraphHome.tsx` | — | kgData, domains | Step 1 图谱首页 |
| `DomainListView` | `pages/DomainListView.tsx` | — | domains | Step 2 域列表 |
| `DomainDesignerView` | `pages/DomainDesignerView.tsx` | domainCode | entities, relationships | Step 3 设计器 |

### 13.2 左侧面板组件

| 组件名 | 文件路径 | Props | 状态依赖 | 说明 |
|--------|---------|-------|---------|------|
| `WorkbenchLeftPanel` | `components/WorkbenchLeftPanel.tsx` | collapsed, onToggle | currentDomain | 左侧面板容器 |
| `DomainSelector` | `components/DomainSelector.tsx` | — | domains, currentDomainCode | 下拉选择切换域 |
| `EntityTreePanel` | `components/EntityTreePanel.tsx` | — | entities, selectedEntityId | 可搜索实体树 |
| `EntityTreeNode` | `components/EntityTreeNode.tsx` | entity, selected | — | 单节点（递归） |
| `EntitySearchBar` | `components/EntitySearchBar.tsx` | onSearch | — | 实体搜索输入框 |

### 13.3 画布组件

| 组件名 | 文件路径 | Props | 状态依赖 | 说明 |
|--------|---------|-------|---------|------|
| `WorkbenchCanvas` | `components/WorkbenchCanvas.tsx` | — | canvasNodes, canvasEdges | 画布容器 |
| `CanvasToolbar` | `components/CanvasToolbar.tsx` | — | — | 工具栏（含 [自动发现] 按钮 v1.1） |
| `KnowledgeGraphView` | `components/KnowledgeGraphView.tsx` | kgData | kgData | Step 1 只读图谱 |
| `DomainGraphCanvas` | `components/DomainGraphCanvas.tsx` | — | canvasNodes, canvasEdges | Step 3 可编辑画布 |
| `EntityNode` | `components/nodes/EntityNode.tsx` | data, selected | — | 自定义实体节点（含 GlossaryBadge v1.1） |
| `RelationshipEdge` | `components/edges/RelationshipEdge.tsx` | data, selected | — | 自定义关系边 |

### 13.4 右侧面板组件

| 组件名 | 文件路径 | Props | 状态依赖 | 说明 |
|--------|---------|-------|---------|------|
| `WorkbenchRightPanel` | `components/WorkbenchRightPanel.tsx` | collapsed, onToggle | selectedEntity | 右侧面板容器 |
| `DomainStatsPanel` | `components/DomainStatsPanel.tsx` | — | domains, currentDomain | 域统计面板 |
| `PropertyEditorPanel` | `components/PropertyEditorPanel.tsx` | — | selectedEntity, properties | 属性编辑器（标签页容器） |
| `EntityBasicInfoForm` | `components/EntityBasicInfoForm.tsx` | entity | — | 基本信息表单 |
| `PropertyTableEditor` | `components/PropertyTableEditor.tsx` | entityId, properties | — | 可编辑属性表 |
| `PropertyRow` | `components/PropertyRow.tsx` | property | — | 属性行（含术语引用 v1.1） |
| `RelationshipListPanel` | `components/RelationshipListPanel.tsx` | entityId, relationships | — | 关系列表 |
| `RelationshipListItem` | `components/RelationshipListItem.tsx` | relationship | — | 关系项 |
| `GlossaryBindingPanel` | `components/panels/GlossaryBindingPanel.tsx` (v1.1) | entityId | entityTermBindings, glossaryTerms | 术语关联面板 |
| `DataSourcePanel` | `components/panels/DataSourcePanel.tsx` (v1.1) | entityId | entityTableMappings, fieldMappings | 数据源面板 |
| `FieldMappingTable` | `components/panels/FieldMappingTable.tsx` (v1.1) | entityId, resourceId | fieldMappings | 字段映射表 |
| `DataPreviewPanel` | `components/panels/DataPreviewPanel.tsx` (v1.1) | resourceId | dataPreview | 数据预览面板 |

### 13.5 弹窗组件

| 组件名 | 文件路径 | Props | 说明 |
|--------|---------|-------|------|
| `CreateEntityModal` | `components/modals/CreateEntityModal.tsx` | open, onClose | 创建实体弹窗 |
| `CreateRelationshipModal` | `components/modals/CreateRelationshipModal.tsx` | open, onClose, sourceId, targetId | 创建关系弹窗 |
| `CreateDomainModal` | `components/modals/CreateDomainModal.tsx` | open, onClose | 创建域弹窗 |
| `DeleteConfirmDialog` | `components/modals/DeleteConfirmDialog.tsx` | open, onClose, entityName, onConfirm | 删除确认 |
| `TermSearchModal` | `components/modals/TermSearchModal.tsx` (v1.1) | open, onClose, onSelect | 术语搜索/选择弹窗 |
| `DataResourcePickerModal` | `components/modals/DataResourcePickerModal.tsx` (v1.1) | open, onClose, onSelect, mode | 数据资源选择弹窗 |

---

## 14. 迁移策略与向后兼容

### 14.1 三阶段迁移

```
Phase 1: 并行运行 (Sprint N)
  ├─ 新建 /ontology-workbench 路由
  ├─ 保留旧路由 /knowledge_graph, /ontology_designer, /ontology
  ├─ 导航菜单新增 "本体工作台" 入口
  └─ 旧页面顶部添加 Banner: "本体工作台已上线，点击体验新版本"

Phase 2: 默认切换 (Sprint N+1)
  ├─ 旧路由改为重定向 → /ontology-workbench
  ├─ 导航菜单默认指向新工作台
  └─ 旧页面保留（通过 URL 直接访问）

Phase 3: 清理 (Sprint N+2)
  ├─ 移除旧页面组件文件
  ├─ 移除旧路由定义
  └─ 清理废弃代码
```

### 14.2 向后兼容矩阵

| 旧入口 | 兼容方式 | 用户体验 |
|--------|---------|---------|
| 导航菜单 "知识图谱" | 指向 `/ontology-workbench` | 直接进入新工作台首页 |
| URL `/knowledge_graph` | 重定向 `/ontology-workbench` | 自动跳转，无感知 |
| URL `/ontology_designer` | 重定向 `/ontology-workbench/domains` | 跳转到域选择页 |
| URL `/ontology` | 重定向 `/ontology-workbench/domains` | 跳转到域选择页 |
| API 端点 | 不变 | 零影响 |
| localStorage 数据 | 新 key 命名空间 | 并行不冲突 |

### 14.3 代码复用映射

| 旧组件/逻辑 | 新组件/位置 | 复用方式 |
|-------------|------------|---------|
| `OntologyExplorer.GraphCanvas` | `KnowledgeGraphView` → `GraphCanvas` | 抽取为共享组件，添加 `readOnly` prop |
| `OntologyDesigner.EntityGraph` | `DomainGraphCanvas` | 连线逻辑抽取到 `useRelationshipConnect` hook |
| `OntologyDesigner` 属性 CRUD 逻辑 | `PropertyTableEditor` | 迁移到 Store actions |
| `KnowledgeGraphPage` 域分组逻辑 | `extractDomainsFromKG()` adapter | 抽取为纯函数 |
| API 调用 (fetchOntologyEntities 等) | `ontologyApi` service | 统一封装 |
| `GlossaryManager` 术语列表 | `TermSearchModal` + `GlossaryBindingPanel` | 抽取术语搜索和展示逻辑 |
| `DataCatalog/DatasetExplorer` 数据资源 | `DataResourcePickerModal` + `DataPreviewPanel` | 抽取资源浏览和预览逻辑 |

---

## 15. 交付计划

### 15.1 Sprint 拆分

| Sprint | 交付内容 | 工期 |
|--------|---------|------|
| **Sprint N** | 基础骨架 + Step 1（图谱首页） | 3天 |
| | - OntologyWorkbenchLayout 三栏布局 | |
| | - KnowledgeGraphHome 页面（全局图谱只读） | |
| | - DomainStatsPanel 统计面板 | |
| | - 路由设置 + 旧路由重定向 | |
| **Sprint N+1** | Step 2 + Step 3 核心画布 | 5天 |
| | - DomainListView 域列表页 | |
| | - DomainGraphCanvas 可编辑画布 | |
| | - EntityNode 自定义节点（拖拽） | |
| | - RelationshipEdge 自定义边 | |
| | - CreateEntityModal 创建实体 | |
| | - CreateRelationshipModal 连线创建关系 | |
| | - Zustand Store 完整实现 | |
| **Sprint N+2** | 右侧面板 + 实体树 + 完善 | 5天 |
| | - EntityTreePanel 实体树导航 | |
| | - PropertyEditorPanel 属性编辑面板 | |
| | - EntityBasicInfoForm 基本信息表单 | |
| | - PropertyTableEditor 属性 CRUD | |
| | - RelationshipListPanel 关系列表 | |
| | - CanvasToolbar 工具栏 | |
| | - 自动布局算法 | |
| | - 错误处理 + loading 状态 | |
| **Sprint N+3** | 术语库集成 + 数据映射 (v1.1) | 5天 |
| | - GlossaryBindingPanel 术语关联面板 | |
| | - TermSearchModal 术语搜索弹窗 | |
| | - PropertyRow 术语引用字段 | |
| | - extractDomainsFromGlossary() 域提取 | |
| | - DataSourcePanel 数据源面板 | |
| | - DataResourcePickerModal 资源选择弹窗 | |
| | - FieldMappingTable 字段映射表 | |
| | - DataPreviewPanel 数据预览 | |
| | - AutoDiscover 自动发现逻辑 | |
| | - 物理类型→本体属性类型映射 | |
| **Sprint N+4** | 打磨 + 旧模块下线 | 3天 |
| | - 交互优化（动画/反馈） | |
| | - 移动端响应式适配 | |
| | - 旧路由 301 重定向 | |
| | - 旧代码清理 | |
| | - 文档更新 | |

**总工期**: 约 21 天（5 个 Sprint，含 v1.1 新增的术语库与数据映射 Sprint）

### 15.2 依赖与风险

| 风险 | 级别 | 缓解措施 |
|------|------|---------|
| 后端 API 返回数据无 domain 字段 | 🟡 中 | Adapter 层兼容处理，v1.1 增加术语库 domain 作为补充来源 |
| ReactFlow 学习曲线 | 🟡 中 | 已有 GraphCanvas/EntityGraph 经验可复用 |
| 旧模块同时在线维护成本 | 🟢 低 | Phase 1 仅添加，不改动旧代码 |
| 状态管理复杂度 | 🟡 中 | Zustand 轻量级，按需拆分 slice |
| 术语绑定数据量 (v1.1) | 🟢 低 | 前端 localStorage，不影响后端 |
| 数据底座 API 不可用 (v1.1) | 🟡 中 | DataSourcePanel 降级显示「数据底座暂不可用」提示；自动发现功能禁用 |

---

## 16. 附录：文件清单

### 16.1 新增文件

```
src/
├── pages/
│   ├── OntologyWorkbenchLayout.tsx          ← 三栏布局骨架
│   ├── KnowledgeGraphHome.tsx              ← Step 1 图谱首页
│   ├── DomainListView.tsx                  ← Step 2 域列表
│   └── DomainDesignerView.tsx              ← Step 3 本体设计器
│
├── components/
│   └── ontology-workbench/
│       ├── WorkbenchLeftPanel.tsx          ← 左侧面板
│       ├── DomainSelector.tsx              ← 域选择器
│       ├── EntityTreePanel.tsx             ← 实体树
│       ├── EntityTreeNode.tsx              ← 实体树节点
│       ├── EntitySearchBar.tsx             ← 搜索条
│       │
│       ├── WorkbenchCanvas.tsx             ← 画布容器
│       ├── CanvasToolbar.tsx               ← 画布工具栏
│       ├── KnowledgeGraphView.tsx          ← 只读全局图谱
│       ├── DomainGraphCanvas.tsx           ← 可编辑域画布
│       │
│       ├── nodes/
│       │   └── EntityNode.tsx              ← 自定义实体节点
│       ├── edges/
│       │   └── RelationshipEdge.tsx        ← 自定义关系边
│       │
│       ├── WorkbenchRightPanel.tsx         ← 右侧面板
│       ├── DomainStatsPanel.tsx            ← 域统计
│       ├── PropertyEditorPanel.tsx         ← 属性编辑器（标签页容器）
│       ├── EntityBasicInfoForm.tsx         ← 基本信息表单
│       ├── PropertyTableEditor.tsx         ← 属性表
│       ├── PropertyRow.tsx                 ← 属性行
│       ├── RelationshipListPanel.tsx       ← 关系列表
│       ├── RelationshipListItem.tsx        ← 关系项
│       │
│       ├── panels/                         ← v1.1 新增子目录
│       │   ├── GlossaryBindingPanel.tsx    ← 术语关联面板
│       │   ├── DataSourcePanel.tsx         ← 数据源面板
│       │   ├── FieldMappingTable.tsx       ← 字段映射表
│       │   └── DataPreviewPanel.tsx        ← 数据预览面板
│       │
│       └── modals/
│           ├── CreateEntityModal.tsx       ← 创建实体
│           ├── CreateRelationshipModal.tsx ← 创建关系
│           ├── CreateDomainModal.tsx       ← 创建域
│           ├── DeleteConfirmDialog.tsx     ← 删除确认
│           ├── TermSearchModal.tsx         ← v1.1 术语搜索弹窗
│           └── DataResourcePickerModal.tsx ← v1.1 数据资源选择弹窗
│
├── stores/
│   └── useWorkbenchStore.ts                ← Zustand 主 Store
│
├── services/
│   ├── ontologyApi.ts                      ← API 客户端（复用现有端点）
│   ├── glossaryClient.ts                   ← v1.1 术语库 API 封装
│   └── dataCatalogClient.ts               ← v1.1 数据底座 API 封装
│
├── adapters/
│   ├── domainAdapter.ts                    ← Domain 数据提取/转换
│   ├── flowAdapter.ts                      ← Entity/Relation → ReactFlow 格式
│   ├── glossaryAdapter.ts                  ← v1.1 术语数据适配
│   └── fieldMappingAdapter.ts             ← v1.1 物理类型→本体类型映射
│
├── hooks/
│   ├── useWorkbenchKeyboard.ts             ← 快捷键 (Delete, Ctrl+S, etc.)
│   ├── useDomainAutoLayout.ts             ← 自动布局 hook
│   ├── useRelationshipConnect.ts          ← 连线验证 + 创建逻辑
│   ├── useGlossaryBinding.ts              ← v1.1 术语关联逻辑
│   └── useAutoDiscovery.ts                ← v1.1 自动发现逻辑
│
└── types/
    └── workbench.ts                        ← TypeScript 类型定义
```

### 16.2 修改文件

```
src/
├── App.tsx                                 ← 添加新路由
├── routes.tsx                              ← 路由表更新
├── components/
│   └── Layout/
│       └── Sidebar.tsx                     ← 导航菜单添加 "本体工作台" 入口
└── types/
    └── index.ts                            ← 新增类型导出
```

### 16.3 移除文件（Phase 3）

```
src/pages/
├── KnowledgeGraphPage.tsx                  ← 迁移完成后移除
├── OntologyDesigner.tsx                    ← 迁移完成后移除
└── OntologyExplorer.tsx                    ← 迁移完成后移除
```

---

## 17. v1.3 需求差距分析与改进计划

> **需求来源**：用户反馈——当前功能设计模糊，需重新理清概念关系并调整实现方向。

### 17.1 四项需求与当前差距

| # | 需求 | 当前状态 | 差距 | 严重度 |
|---|------|---------|------|--------|
| **1** | 菜单"图谱工作台"→"本体工作台" | Sidebar 第119行 `labelZh: "图谱工作台"` | 1行改名，差距极小 | 🟢 低 |
| **2** | 左上角"业务域"→"本体/业务域"，集成 OntologyDesigner CRUD + 术语 + 规则 | 当前左面板仅有域选择器 + 实体树，OntologyDesigner 是独立页面 | 缺少：本体CRUD表单、术语管理入口、规则定义能力、OntologyDesigner的实体CRUD未集成进左面板 | 🔴 关键 |
| **3** | 画布支持本体下知识图谱构建 + 全局图谱浏览 | 当前 Step1(KnowledgeGraphHome) 只有全局只读，Step3(DomainDesignerView) 是独立域编辑 | 缺少：画布内全局/域视图切换按钮；全局图谱不可编辑；域图谱无法一键切换 | 🟡 重要 |
| **4** | 打通与数据底座之间的映射 | `ecos_entity_table_mapping` 表为空(0行)；DataSourcePanel 虽有UI但无真实数据 | 缺少：映射API后端实现、AutoDiscover联动、映射数据持久化 | 🔴 关键 |

### 17.2 概念层变化（v1.2 → v1.3）

```
v1.2 层次:   Domain → Ontology → Entity → Property/Relation/Glossary
v1.3 层次:   Ontology≈Domain → Entity → Property/Relation/Glossary/Rule

关键变化：
  • Domain 和 Ontology 合并为一个概念（本体/业务域）
  • 新增"规则(Rule)"作为本体下一级子概念
  • 左侧面板从"域选择器"升级为"本体工作区"（含CRUD+术语+规则）
```

### 17.3 布局调整

```
v1.2 布局（三步骤，三个路由）:
  Step 1: /ontology-workbench → KnowledgeGraphHome（全局只读）
  Step 2: /ontology-workbench/domains → DomainListView（域列表）
  Step 3: /ontology-workbench/domains/:code → DomainDesignerView（域编辑）

v1.3 布局（单页，画布内切换）:
  /ontology_workbench → OntologyWorkbenchLayout（单一页面）
    ├── 左面板: 本体选择器 + 实体CRUD + 术语管理 + 规则定义
    ├── 中画布: [全局图谱] ⇄ [本体图谱] 切换按钮
    └── 右面板: 属性编辑器 + 数据映射（不变）
```

### 17.4 改进任务拆解

#### Phase A: 菜单 + 路由简化（0.5天）

| 任务 | 内容 | 涉及文件 |
|------|------|---------|
| A1 | Sidebar "图谱工作台" → "本体工作台" | `Sidebar.tsx:119` |
| A2 | 移除 `/domains`、`/domain_designer` 路由，统一到 `/ontology_workbench` | `main.tsx` |
| A3 | 更新 `OntologyWorkbench.tsx` 入口：从 KnowledgeGraphHome 改为 OntologyWorkbenchLayout | `OntologyWorkbench.tsx` |

#### Phase B: 左面板重构为"本体工作区"（2天）

| 任务 | 内容 | 涉及文件 |
|------|------|---------|
| B1 | 创建 `OntologyDomainPanel` 组件，替代当前的 DomainSelector + EntityTreePanel | 新建 |
| B2 | 集成 OntologyDesigner 的实体 CRUD（实体列表+添加+编辑+删除）到左面板 | 新建/复用 `OntologyDesigner.tsx` |
| B3 | 添加"术语管理"标签页——显示当前本体关联的术语列表，支持 CRUD | 新建 |
| B4 | 添加"规则定义"标签页——业务规则列表（约束/推导/校验），支持 CRUD | 新建 |
| B5 | 本体选择器：下拉切换本体（供应链本体/财务本体/…） | 新建 |

**左面板目标布局**：
```
┌──────────────────────┐
│ [本体选择器 ▼]        │  ← 下拉切换本体（供应链本体/财务本体/…）
├──────────────────────┤
│ [实体] [术语] [规则]  │  ← 标签页切换
├──────────────────────┤
│ ┌──────────────────┐ │
│ │ + 新增实体        │ │  ← 来自 OntologyDesigner
│ │ 供应商 (MASTER)   │ │
│ │ 物料 (MASTER)     │ │
│ │ 订单 (TRANSACTION)│ │
│ │ …                │ │
│ └──────────────────┘ │
│                      │
│ 术语标签页：          │
│ ┌──────────────────┐ │
│ │ + 新增术语        │ │
│ │ 供应商: 依法…     │ │
│ │ 物料: 生产所需…   │ │
│ └──────────────────┘ │
│                      │
│ 规则标签页：          │
│ ┌──────────────────┐ │
│ │ + 新增规则        │ │
│ │ 信用额度上限      │ │
│ │ 物料有效期校验    │ │
│ └──────────────────┘ │
└──────────────────────┘
```

#### Phase C: 画布双视图切换（1天）

| 任务 | 内容 | 涉及文件 |
|------|------|---------|
| C1 | 合并 KnowledgeGraphHome 的全局图谱 + DomainGraphCanvas 的域图谱到同一画布 | `OntologyWorkbenchLayout.tsx` |
| C2 | 画布顶部工具栏增加 [全局图谱] / [本体图谱] 切换按钮 | `CanvasToolbar.tsx` |
| C3 | 全局图谱模式：显示所有域的所有实体+关系（只读浏览） | `KnowledgeGraphView` |
| C4 | 本体图谱模式：仅显示当前选中本体的实体+关系（可编辑） | `DomainGraphCanvas` |
| C5 | 切换时保持画布位置（zoom/pan 不变） | Store |

#### Phase D: 数据底座映射打通（2天）

| 任务 | 内容 | 涉及文件 |
|------|------|---------|
| D1 | 检查后端 `ecos_entity_table_mapping` API 是否存在，不存在则需新增 | 后端 |
| D2 | 实现 DataSourcePanel 的完整流程：选择数据资源→字段映射→保存映射 | `DataSourcePanel.tsx` |
| D3 | 实现 AutoDiscover：根据表名模糊匹配实体→自动创建属性→建立映射 | `CanvasToolbar.tsx` |
| D4 | 映射数据从 localStorage 改为通过 API 持久化到数据库 | Store → API |
| D5 | 映射状态指示：已映射🟢 / 待同步🟡 / 未映射⚪ | `EntityNode.tsx` |

### 17.5 资源评估

| Phase | 工作内容 | 工期 | 依赖 |
|-------|---------|------|------|
| A | 菜单+路由简化 | 0.5天 | 无 |
| B | 左面板本体工作区 | 2天 | A |
| C | 画布双视图 | 1天 | A |
| D | 数据底座映射 | 2天 | 需确认后端API |
| **合计** | | **5.5天** | |

### 17.6 风险

| 风险 | 级别 | 缓解 |
|------|------|------|
| OntologyDesigner 代码与 workbench store 不兼容 | 🟡 中 | 提取纯函数 + adapter，不直接复用组件状态 |
| `ecos_entity_table_mapping` 后端 API 缺失 | 🔴 高 | 先确认API是否存在；若不存在，用 localStorage 过渡 + 新建后端端点 |
| 规则定义无后端支持 | 🟡 中 | 前端先用 localStorage + 预定义规则模板，后端新增 `ecos_rule` 表后续跟进 |
| 多本体切换时数据加载延迟 | 🟢 低 | Store 按本体ID缓存，切换时先显示缓存再刷新 |

---

## 18. v1.4 需求改进（2026-07-02）

### 18.1 六项需求完成情况

| # | 需求 | 状态 | 说明 |
|---|------|------|------|
| 1 | 本体图谱放前，全局图谱放后 | ✅ | 画布标签页顺序交换，默认显示本体图谱 |
| 2 | 本体无CRUD功能 | ✅ | 左面板简化为只读本体列表，移除增删改 |
| 3 | 种子数据（5核心实体） | ✅ | 保留 Supplier/Order/Project/Product/Equipment，删除多余实体 |
| 4 | 点击本体进入图谱构建面板（拖拽） | ✅ | 左面板选本体→DomainGraphCanvas(ReactFlow)可拖拽创建修改 |
| 5 | 打通数据底座映射 | ✅ | 新建 EntityTableMappingController，4个REST端点 |
| 6 | 画布操作方式全系统一致 | ✅ | 统一使用 DomainGraphCanvas + ReactFlow + 自定义 EntityNode/RelationshipEdge |

### 18.2 变更清单

| 文件 | 变更 | 说明 |
|------|------|------|
| `OntologyWorkbenchLayout.tsx` | 重写 | 默认本体图谱、简化布局、使用 DomainGraphCanvas |
| `OntologyDomainPanel.tsx` | 重写 | 575→167行，从三标签页改为只读本体列表 |
| `EntityTableMappingController.java` | 新建 | 4端点：list/create/delete/datasource |
| 数据库实体 | 清理 | 删除6个多余实体+Invoice，保留5核心 |

### 18.3 当前架构

```
┌──────────────┬─────────────────────────┬──────────────┐
│  LEFT 240px  │      CENTER flex-1       │  RIGHT 320px │
├──────────────┼─────────────────────────┼──────────────┤
│ 本体列表      │ [本体图谱] [全局图谱]     │ 属性编辑器    │
│ (只读)       │                         │ 数据映射      │
│              │ 本体图谱: DomainGraphCanvas│              │
│ 供应链本体 ◉  │  (ReactFlow 拖拽式)      │              │
│ 财务本体      │ 全局图谱: KnowledgeGraph  │              │
│              │  (只读浏览)              │              │
└──────────────┴─────────────────────────┴──────────────┘
```

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-07-01 | 初始版本，完整设计文档（三模块整合） | ECOS-ARCH |
| v1.1 | 2026-07-01 | 新增术语库集成（第11章）和数据映射/同步（第12章）；扩展组件树、状态管理、API兼容、Sprint计划、文件清单 | ECOS-ARCH |
| v1.2 | 2026-07-02 | 新增第0章核心概念体系定义（8个概念 + DIKW金字塔 + 5组辨析 + 10个偏差） | ECOS-ARCH + ECOS-PM + ECOS-PMO |
| v1.3 | 2026-07-02 | 重构：本体≈业务域、单页工作台、左面板集成实体CRUD+术语+规则、画布双视图切换、数据底座映射计划（新增第17章差距分析） | ECOS-PMO |
| v1.4 | 2026-07-02 | 左面板简化为只读本体列表、本体图谱默认显示+ReactFlow拖拽、种子数据清理（5核心实体）、映射控制器4端点、全系统画布统一 | ECOS-PMO |

---

> **文档状态**: Draft  
> **审批**: 待 PM / Tech Lead Review  
> **关联文档**: 
> - [S1-CORE02-OntologyDesigner-HLD.md](../03-系统设计/S1-CORE02-OntologyDesigner-HLD.md)  
> - [10-Knowledge Graph设计说明书](../03-系统设计/10-Knowledge%20Graph设计说明书.txt)  
> - [07-Ontology元模型设计说明书](../03-系统设计/07-Ontology元模型设计说明书.txt)  
> - [ECOS-功能需求规格说明书](../01-产品设计/ECOS-功能需求规格说明书.md)
