# PMO指令：数据工作台 — 9页整合为1个工作台

> **来源**: `/home/guorongxiao/ECOS/03-设计文档/data-workbench-design.md` §六 Phase 2
> **日期**: 2026-07-02
> **参考**: Ontology Workbench整合模式（3页→1工作台，8815行/27文件）
> **铁律**: 单文件粒度，每个Task改一个文件。Git提交=唯一绩效。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 背景

前端当前有9个独立的数据相关页面，数据工程师需要在页面之间来回跳转：

| 页面 | 路由 | 行数估算 |
|------|------|:--:|
| DataCatalog | `/catalog` | ~800 |
| DatasetExplorer | `/dataset_explorer` | ~600 |
| DataLineage | `/lineage` | ~500 |
| DataQualityDashboard | `/dq_dashboard` | ~700 |
| DataLake | `/datalake` | ~400 |
| DataSourceManager | `/datasources` | ~600 |
| PipelineBuilder | `/pipeline` | ~900 |
| CodeWorkbook | `/workbook` | ~500 |
| TaskCenter | `/task_center` | ~500 |

**目标**：整合为一个统一的数据工作台 `/data-workbench`，5个分区标签页切换。布局参照已完成的Ontology Workbench（三栏：左导航+中画布+右属性面板）。

---

## 禁止清单

1. 禁止新建Maven模块或后端API（100%复用现有端点）
2. 禁止修改原有9个页面的核心业务逻辑——只搬迁不改逻辑
3. 禁止产出分析文档——唯一产出是git commit
4. 禁止删除旧页面文件——旧路由重定向后保留原文件，Phase 3再清理
5. 禁止新增npm依赖

---

## 架构总览

### 三栏布局

```
┌────────────┬──────────────────────────┬─────────────────────┐
│  左侧面板   │        中间画布           │     右侧面板         │
│  (260px)   │       (flex: 1)          │     (320px)         │
│            │                          │                     │
│ 分区导航    │  5个分区（标签页切换）     │  属性/详情面板       │
│            │                          │                     │
│ 📡 数据源   │  分区1: 数据源网格         │  · 选中资产属性      │
│ 🔧 管道     │  分区2: 管道DAG画布       │  · 执行日志          │
│ 📊 治理     │  分区3: 治理仪表盘        │  · 血缘信息          │
│ 🔗 血缘     │  分区4: 血缘图            │                     │
│ ⏱ 调度     │  分区5: 任务DAG           │                     │
│            │                          │                     │
└────────────┴──────────────────────────┴─────────────────────┘
```

### 路由设计

```
新路由                                旧路由（重定向）
──────────────────────────────────  ──────────────────────────
/data-workbench                       ← /catalog (默认首页)
/data-workbench/sources               ← /datasources + /datalake
/data-workbench/pipelines             ← /pipeline + /workbook
/data-workbench/governance            ← /dataset_explorer + /dq_dashboard
/data-workbench/lineage               ← /lineage
/data-workbench/tasks                 ← /task_center
```

### 组件树

```
DataWorkbenchLayout                         ← 新建：三栏布局容器
│
├── WorkbenchLeftPanel
│   └── PartitionNav                        ← 5个导航项（图标+名称+计数角标）
│       ├── NavItem("数据源", icon, count)
│       ├── NavItem("管道", icon, count)
│       ├── NavItem("治理", icon, count)
│       ├── NavItem("血缘", icon, count)
│       └── NavItem("调度", icon, count)
│
├── WorkbenchCanvas（按路由渲染对应分区）
│   ├── SourcesView                         ← 整合 DataSourceManager + DataLake
│   │   ├── ConnectionGrid                  ← 数据源卡片列表
│   │   ├── ConnectionForm                  ← 新建/编辑连接表单
│   │   └── DataPreviewTable                ← 数据预览
│   │
│   ├── PipelinesView                       ← 整合 PipelineBuilder + CodeWorkbook
│   │   ├── PipelineToolbar                 ← 工具栏（新建/保存/运行）
│   │   ├── PipelineCanvas (ReactFlow)      ← 管道DAG画布
│   │   ├── NodeLibrary                     ← 算子面板（可拖拽）
│   │   └── CodeEditor                      ← SQL/Python编辑器（从CodeWorkbook搬迁）
│   │
│   ├── GovernanceView                      ← 整合 DataCatalog + DatasetExplorer + DQDashboard
│   │   ├── CatalogTree                     ← 资产目录树
│   │   ├── QualityRuleList                 ← 质量规则列表
│   │   └── QualityCharts                   ← 质量仪表盘图表
│   │
│   ├── LineageView                         ← 从 DataLineage 搬迁
│   │   └── LineageGraph (ReactFlow)        ← 血缘图（只读）
│   │
│   └── TasksView                           ← 从 TaskCenter 搬迁
│       ├── TaskList                        ← 任务列表
│       ├── TaskDAG                         ← 任务依赖图
│       └── ExecutionHistory                ← 执行历史
│
└── WorkbenchRightPanel
    ├── AssetDetailPanel                     ← 选中资产详情
    ├── ExecutionLogPanel                    ← 管道/任务执行日志
    └── EmptyState                          ← 未选中时的占位
```

---

## P0：布局容器 + 路由（2天）

### T0.1: DataWorkbenchLayout 三栏容器（0.5天，FE）

**目标**: 创建数据工作台的顶层布局组件，提供左中右三栏结构。

**改文件**: `c2eos/src/pages/DataWorkbenchLayout.tsx`（新建）

**实现要点**:
- 三栏flex布局：左260px + 中flex:1 + 右320px
- 使用React Router `<Outlet />` 渲染子路由对应的分区组件
- 左侧 `PartitionNav` 渲染5个导航项，点击跳转对应子路由
- 右侧面板根据当前选中的资产动态渲染
- 响应式：屏幕宽度<1200px时右侧面板可折叠

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep "DataWorkbenchLayout"
# 期望: 0行（零TS错误）

# 浏览器验证: 访问 /data-workbench → 看到三栏布局骨架
```

---

### T0.2: 路由注册 + 旧路由重定向（0.5天，FE）

**目标**: 在main.tsx中注册新路由，旧9个路由重定向到新路径。

**改文件**: `c2eos/src/main.tsx`

**操作**:
1. 新增路由组:
```tsx
<Route path="data-workbench" element={<DataWorkbenchLayout />}>
  <Route index element={<Navigate to="/data-workbench/sources" replace />} />
  <Route path="sources" element={<SourcesView />} />
  <Route path="pipelines" element={<PipelinesView />} />
  <Route path="governance" element={<GovernanceView />} />
  <Route path="lineage" element={<LineageView />} />
  <Route path="tasks" element={<TasksView />} />
</Route>
```

2. 旧路由改为重定向（保留旧文件，不删除）:
```tsx
<Route path="catalog" element={<Navigate to="/data-workbench/sources" replace />} />
<Route path="datasources" element={<Navigate to="/data-workbench/sources" replace />} />
<Route path="datalake" element={<Navigate to="/data-workbench/sources" replace />} />
<Route path="pipeline" element={<Navigate to="/data-workbench/pipelines" replace />} />
<Route path="workbook" element={<Navigate to="/data-workbench/pipelines" replace />} />
<Route path="dataset_explorer" element={<Navigate to="/data-workbench/governance" replace />} />
<Route path="dq_dashboard" element={<Navigate to="/data-workbench/governance" replace />} />
<Route path="lineage" element={<Navigate to="/data-workbench/lineage" replace />} />
<Route path="task_center" element={<Navigate to="/data-workbench/tasks" replace />} />
```

**验收**:
```bash
# 浏览器验证: 访问旧路由 /catalog → 自动跳转到 /data-workbench/sources
# 访问 /pipeline → 自动跳转到 /data-workbench/pipelines
```

---

### T0.3: PartitionNav 导航组件（0.5天，FE）

**目标**: 左侧5分区导航，显示各分区资产计数。

**改文件**: `c2eos/src/components/data-workbench/PartitionNav.tsx`（新建）

**实现要点**:
- 5个导航项：数据源、管道、治理、血缘、调度
- 每个导航项显示图标 + 名称 + 数量角标
- 当前激活分区高亮
- 点击跳转对应子路由
- 角标数据从各分区API获取（如数据源数=GET /api/v1/datanet/metadata/resources/all的length）

**验收**:
```bash
npx tsc --noEmit 2>&1 | grep "PartitionNav"
# 期望: 0行
```

---

### T0.4: WorkbenchRightPanel（0.5天，FE）

**目标**: 右侧属性/详情面板，根据选中资产动态显示。

**改文件**: `c2eos/src/components/data-workbench/WorkbenchRightPanel.tsx`（新建）

**实现要点**:
- 接收 `selectedAsset` prop（类型: {type: 'source'|'pipeline'|'rule'|'task', id: string}）
- 根据type加载对应详情API
- 未选中时显示EmptyState："选择一个资产查看详情"
- 底部常驻关闭/折叠按钮

**验收**:
```bash
npx tsc --noEmit 2>&1 | grep "WorkbenchRightPanel"
# 期望: 0行
```

---

## P1：5个分区View（3.5天）

### T1.1: SourcesView — 数据源管理（0.5天，FE）

**目标**: 整合DataSourceManager的数据源CRUD + DataLake的数据预览。

**改文件**: `c2eos/src/components/data-workbench/SourcesView.tsx`（新建）

**复用策略**: 从 `DataSourceManager.tsx` 中提取 ConnectionGrid + ConnectionForm 逻辑，不复制粘贴——抽取为共享组件或直接在此文件中重写（参考旧代码，简化实现）。

**功能**:
- 数据源卡片网格（类型图标 + 名称 + 状态 + 最近同步时间）
- 点击卡片 → 右侧面板显示详情
- [添加数据源]按钮 → 弹出ConnectionForm
- [测试连接]按钮 → 调用后端测试端点
- 底部数据预览表（从DataLake搬迁）

**后端API**: 100%复用 `DataSourceController` + `MetadataController`

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/v1/datanet/data-sources" | jq '.data | length'
# 期望: 返回已注册的数据源数量（>=0，至少有一个种子数据源）

curl -s -H "$H" "http://localhost:8080/api/v1/datanet/metadata/resources/all" | jq '.data | length'
# 期望: >0
```

---

### T1.2: PipelinesView — 管道开发（1.5天，FE）

**目标**: 整合PipelineBuilder的DAG画布 + CodeWorkbook的SQL/Python编辑器。

**改文件**: `c2eos/src/components/data-workbench/PipelinesView.tsx`（新建）

**这是最重的分区**。管道画布基于ReactFlow，需要：
- 左侧算子库面板（可拖拽到画布）
- 中间ReactFlow画布（算子节点 + 连线）
- 底部代码编辑器（Monaco或TextArea，从CodeWorkbook搬迁逻辑）

**复用策略**: 
- 画布逻辑参考 `PipelineBuilder.tsx` 的ReactFlow实现
- 代码编辑器参考 `CodeWorkbook.tsx`
- 不修改原有文件，在新文件中重新组织

**后端API**: 复用 `PipelineController` + `PipelineExecutionService`

**验收**:
```bash
# 查询管道列表
curl -s -H "$H" "http://localhost:8080/api/v1/datanet/pipelines" | jq '.data | length'
# 期望: 返回管道数量

# 浏览器: 拖拽算子到画布 → 连线 → 点击保存 → 无报错
```

---

### T1.3: GovernanceView — 数据治理（0.5天，FE）

**目标**: 整合DataCatalog的资产目录 + DataQualityDashboard的质量仪表盘。

**改文件**: `c2eos/src/components/data-workbench/GovernanceView.tsx`（新建）

**功能**:
- 左半部分：资产目录树（按数据源/类型分组）
- 右半部分：质量仪表盘（规则数/问题数/健康评分）
- 点击目录节点 → 右侧面板显示资产详情
- 点击质量规则 → 右侧面板显示规则定义

**后端API**: 复用 `CatalogController` + `DqDashboardController`

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/dq/dashboard" | jq '.data.rules | length'
# 期望: >=0
```

---

### T1.4: LineageView — 血缘分析（0.5天，FE）

**目标**: 从DataLineage搬迁血缘图功能。

**改文件**: `c2eos/src/components/data-workbench/LineageView.tsx`（新建）

**复用策略**: 直接引用 `DataLineage.tsx` 的血缘图渲染逻辑。如果DataLineage已有独立组件，直接import复用。

**后端API**: `LineageRecorder`（runtime-core）需确认是否已暴露REST端点。

如果没有 → 新增一个轻量端点：
```java
// 在Gateway中新增
@GetMapping("/api/v1/ecos/lineage/{objectId}")
ApiResponse<LineageGraph> getLineage(@PathVariable String objectId);
```

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/lineage/test-object-1" | jq '.data'
# 期望: 返回节点+边（可以是空图，但端点要200）
```

---

### T1.5: TasksView — 任务调度（0.5天，FE）

**目标**: 从TaskCenter搬迁任务列表+执行历史。

**改文件**: `c2eos/src/components/data-workbench/TasksView.tsx`（新建）

**复用策略**: 从 `TaskCenter.tsx` 搬迁任务列表UI逻辑。

**后端API**: `TaskManagementService`（runtime-core）

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/v1/ecos/tasks" | jq '.data | length'
# 期望: >=0
```

---

## P2：数据源种子数据 + 端到端验证（0.5天）

### T2.1: 种子数据确保（0.25天，BE/SQL）

**目标**: 确保至少有一个数据源连接在数据库中，SourcesView打开时不显示空状态。

**改文件**: SQL迁移或直接INSERT

```sql
-- 确保 td_data_resource 至少有一条记录
INSERT INTO td_data_resource (id, name, type, connection_info, status, created_at)
VALUES ('ds001', 'ECOS本地PostgreSQL', 'POSTGRESQL', 
  '{"host":"localhost","port":5432,"database":"sys_man","username":"root"}',
  'ACTIVE', NOW())
ON CONFLICT (id) DO NOTHING;
```

---

### T2.2: 端到端验证（0.25天）

**目标**: 从浏览器走通完整的数据工程师工作流。

**验证流程**:
1. 打开 `/data-workbench` → 默认显示SourcesView，看到一个数据源
2. 点击"管道"导航 → 切换到PipelinesView
3. 拖拽一个算子到画布 → 保存管道 → 无报错
4. 点击"治理"导航 → 看到质量仪表盘
5. 访问旧路由 `/catalog` → 自动跳转到 `/data-workbench/sources`
6. 访问旧路由 `/pipeline` → 自动跳转到 `/data-workbench/pipelines`

---

## 执行顺序

```
P0 布局容器+路由（2天）
├── T0.1 DataWorkbenchLayout 三栏容器 (0.5天)
├── T0.2 路由注册+旧路由重定向   (0.5天)
├── T0.3 PartitionNav 导航组件   (0.5天)
└── T0.4 WorkbenchRightPanel     (0.5天)

P1 5个分区View（3.5天）
├── T1.1 SourcesView   (0.5天)  ← 最简单，先做
├── T1.2 PipelinesView (1.5天)  ← 最重
├── T1.3 GovernanceView(0.5天)
├── T1.4 LineageView    (0.5天)
└── T1.5 TasksView      (0.5天)

P2 种子数据+验证（0.5天）
├── T2.1 种子数据 (0.25天)
└── T2.2 端到端验证 (0.25天)
```

**总工时**: 6天。T1.2（管道画布）是最长路径。

---

## 交付检查清单

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | 三栏布局渲染 | 浏览器截图 |
| 2 | 5分区导航切换 | 点击每个导航项→分区切换无闪烁 |
| 3 | 旧路由全部重定向 | 访问9个旧URL→全部跳到新路径 |
| 4 | SourcesView显示数据源列表 | 至少1个数据源卡片 |
| 5 | PipelinesView画布可拖拽连线 | 拖拽算子→连线→保存 |
| 6 | GovernanceView显示质量仪表盘 | 图表渲染 |
| 7 | LineageView可查看血缘图 | 画布渲染 |
| 8 | TasksView可查看任务列表 | 列表渲染 |
| 9 | 右侧面板随选中刷新 | 选中数据源→右侧显示详情 |
| 10 | 零TS错误（新文件） | `npx tsc --noEmit | grep data-workbench` = 0 |

---

**一句话给PMO**: 参照Ontology Workbench的整合模式（三栏+标签页），把9个页面装进1个工作台。不改任何后端API，不改任何业务逻辑——只搬UI、组布局、设路由。
