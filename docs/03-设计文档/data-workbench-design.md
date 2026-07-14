# ECOS数据工作台整合方案

> **日期**: 2026-07-02
> **参考**: Ontology Workbench整合经验（8815行/27文件，3→1成功）

---

## 一、产品定位

**数据工作台 = 数据工程师的一站式操作界面**

数据工程师在这里完成所有数据相关工作——数据源接入、管道开发、数据治理、知识图谱构建、Agent开发——所有工作成果通过Git自动版本化。不再需要在9个页面之间跳转。

**一句话**：本体工作台是"业务分析师定义业务语义"的地方，数据工作台是"数据工程师把语义落地到数据"的地方。

---

## 二、架构设计

### 2.1 DIKW四层中的位置

```
W 智慧层 — Agent开发                                 ← Agent工作台（未来）
K 知识层 — 本体定义 / 因果链 / 术语        ← 本体工作台（已完成整合）
I 信息层 — 对象运行时
D 数据层 — 数据源 / 管道 / 质量 / 血缘 / Git ← ★ 数据工作台（本次整合）
```

### 2.2 Git版本管理覆盖范围

```
数据工作台中所有可编辑的资产 → Git自动版本化

┌─────────────────────────────────────────────────────────┐
│ 资产类型          存储形式           Git托管            │
├─────────────────────────────────────────────────────────┤
│ 数据管道          Pipeline JSON     git repo:/pipelines/│
│ 数据质量规则      DQ Rule JSON      git repo:/dq-rules/ │
│ 数据转换脚本      .sql / .py        git repo:/scripts/  │
│ 数据源配置        Connection JSON   git repo:/connections/│
│ 本体→物理表映射   Mapping JSON      git repo:/mappings/ │
│ Agent工具配置     Tool YAML         git repo:/agents/   │
│ 知识图谱Schema    KG Schema YAML    git repo:/kg/       │
└─────────────────────────────────────────────────────────┘
```

**版本操作**：每次保存 → auto-commit。每次发布 → auto-tag。每次回滚 → git revert + 重新部署。

### 2.3 页面整合方案

9页 → 1个工作台，5个分区（标签页切换）：

```
数据工作台 /data-workbench
│
├── 分区1: 数据源管理（原 DataSourceManager + DataLake）
│   数据源注册/测试连接/元数据浏览/数据预览
│
├── 分区2: 管道开发（原 PipelineBuilder + CodeWorkbook）
│   可视化管道编辑器 + SQL/Python Notebook + 管道执行/监控
│
├── 分区3: 数据治理（原 DataCatalog + DatasetExplorer + DataQualityDashboard）
│   资产目录/数据质量规则/质量仪表盘
│
├── 分区4: 血缘与影响分析（原 DataLineage）
│   数据血缘图/影响分析/变更通知
│
└── 分区5: 任务调度（原 TaskCenter）
│   定时任务/依赖编排/执行历史
```

**Git版本侧边栏（全局）**：数据工作台左侧常驻Git面板——显示当前分支、未提交变更、提交历史。分区切换时Git面板保持可见。

---

## 三、Git版本管理详细设计

### 3.1 核心模型

```
Git Workspace（数据工程师的工作区）
  ├── 绑定一个 Git Repository（可以是本地或远程）
  ├── 包含多个 Asset Group（按资产类型分组）
  │   ├── pipelines/
  │   ├── dq-rules/
  │   ├── scripts/
  │   ├── mappings/
  │   ├── agents/
  │   └── kg-schemas/
  └── 每个 Asset Group 对应数据工作台的一个分区
```

### 3.2 自动化版本策略

| 操作 | Git行为 |
|------|---------|
| 在管道编辑器中保存 | `git commit -m "pipeline: {name} updated"` |
| 批量修改后发布 | `git tag v{date}-{seq}` |
| 从历史版本恢复 | `git checkout {commit} -- {file}` → 重新加载 |
| 查看版本差异 | `git diff {v1}..{v2} -- {file}` |
| 分支管理 | 开发/测试/生产 三个长期分支 |

### 3.3 复用策略

**后端**：100%复用 `runtime-core/git/` 模块（GitService/GitRepositoryService/GitServiceImpl），增加一个REST Controller暴露Git操作为API。

**前端**：新增 `GitPanel` 组件（左侧常驻，~300行），调用Git API。

---

## 四、前端架构（参照Ontology Workbench模式）

### 4.1 路由设计

```
新路由                                旧路由（重定向）
──────────────────────────────────  ──────────────────────────
/data-workbench                       ← /catalog
/data-workbench/sources               ← /datasources + /datalake
/data-workbench/pipelines             ← /pipeline + /workbook
/data-workbench/governance            ← /dq_dashboard + /dataset_explorer
/data-workbench/lineage               ← /lineage
/data-workbench/tasks                 ← /task_center
```

### 4.2 三栏布局（同Ontology Workbench）

```
┌────────────┬──────────────────────────┬─────────────────────┐
│  左侧面板   │        中间画布           │     右侧面板         │
│  (280px)   │       (flex: 1)          │     (320px)         │
│            │                          │                     │
│ ┌────────┐ │  分区内容（标签页切换）    │  资产属性编辑器      │
│ │Git面板  │ │                          │  · 基本信息          │
│ │        │ │  分区1: 数据源网格         │  · 版本历史          │
│ │· 分支   │ │  分区2: 管道画布          │  · 执行日志          │
│ │· 变更   │ │  分区3: 治理仪表盘        │  · 依赖关系          │
│ │· 历史   │ │  分区4: 血缘图            │                     │
│ └────────┘ │  分区5: 任务DAG           │                     │
│            │                          │                     │
│ ┌────────┐ │                          │                     │
│ │分区导航 │ │                          │                     │
│ │· 数据源 │ │                          │                     │
│ │· 管道   │ │                          │                     │
│ │· 治理   │ │                          │                     │
│ │· 血缘   │ │                          │                     │
│ │· 调度   │ │                          │                     │
│ └────────┘ │                          │                     │
└────────────┴──────────────────────────┴─────────────────────┘
```

### 4.3 组件树

```
DataWorkbenchLayout                           ← 顶层布局容器
├── WorkbenchLeftPanel
│   ├── GitPanel                              ← Git版本面板（常驻）
│   │   ├── BranchSelector                    ← 分支选择
│   │   ├── UncommittedChangesList            ← 未提交变更
│   │   ├── CommitHistoryList                 ← 提交历史
│   │   └── GitActionBar                      ← commit/push/pull/tag
│   └── PartitionNav                          ← 分区导航
│       ├── SourcesNavItem
│       ├── PipelinesNavItem
│       ├── GovernanceNavItem
│       ├── LineageNavItem
│       └── TasksNavItem
├── WorkbenchCanvas（分区内容，标签切换）
│   ├── SourcesView                           ← 数据源管理
│   │   ├── ConnectionGrid                    ← 数据源卡片网格
│   │   └── ConnectionTestPanel               ← 连接测试
│   ├── PipelinesView                         ← 管道开发
│   │   ├── PipelineCanvas (ReactFlow)        ← 可视化管道编辑
│   │   ├── NodeLibrary                       ← 算子库
│   │   └── PipelineRunPanel                  ← 运行/监控
│   ├── GovernanceView                        ← 数据治理
│   │   ├── CatalogTree                       ← 资产目录树
│   │   ├── QualityRuleEditor                 ← 质量规则编辑
│   │   └── QualityDashboard                  ← 质量仪表盘
│   ├── LineageView                           ← 血缘分析
│   │   └── LineageGraph (ReactFlow)          ← 血缘图（只读）
│   └── TasksView                             ← 任务调度
│       ├── TaskDAG                           ← 任务依赖图
│       └── ExecutionHistory                  ← 执行历史
└── WorkbenchRightPanel
    ├── AssetPropertyEditor                    ← 资产属性
    ├── VersionHistoryPanel                    ← 版本历史
    └── ExecutionLogPanel                      ← 执行日志
```

---

## 五、后端API策略

### 5.1 完全复用现有API

| 分区 | 现有后端API | 状态 |
|------|-----------|:--:|
| 数据源管理 | `DataSourceController` + `MetadataController` | ✅ 已有 |
| 管道开发 | `PipelineController` + `PipelineExecutionService` | ✅ 已有 |
| 数据治理 | `DqDashboardController` + `CatalogController` | ✅ 已有 |
| 血缘分析 | `LineageRecorder` (runtime-core) | ⚠️ 需暴露REST端点 |
| 任务调度 | `TaskManagementService` + `TaskSchedulerService` | ⚠️ 需暴露REST端点 |

### 5.2 仅需新增一个Git Controller

```java
// GitController.java (新增，~200行)
@RestController
@RequestMapping("/api/v1/ecos/git")
public class GitController {
    GET    /status              → 当前仓库状态（分支/未提交文件）
    GET    /commits?path=       → 文件提交历史
    GET    /diff?commit1=&commit2=&path= → 版本差异
    POST   /commit              → 提交变更
    POST   /tag                 → 打标签
    POST   /rollback            → 回滚文件到指定版本
    GET    /branches            → 分支列表
    POST   /branch              → 创建/切换分支
}
```

---

## 六、分阶段实施计划

### Phase 1: Git版本管理基础设施（1周）

**目标**: Git面板可用，Pipeline和DQ规则能自动commit。

| Task | 内容 | 工期 |
|------|------|:--:|
| T1.1 | 新增 `GitController`（8个端点） | 2天 |
| T1.2 | 前端 `GitPanel` 组件 + 集成到工作台布局 | 1天 |
| T1.3 | Pipeline保存时触发auto-commit | 1天 |
| T1.4 | DQ规则保存时触发auto-commit | 0.5天 |

### Phase 2: 数据工作台布局整合（1.5周）

**目标**: 9页整合为1个工作台，5个分区可切换。

| Task | 内容 | 工期 |
|------|------|:--:|
| T2.1 | `DataWorkbenchLayout` 三栏布局容器 | 1天 |
| T2.2 | `SourcesView` 整合DataSourceManager+DataLake | 1天 |
| T2.3 | `PipelinesView` 整合PipelineBuilder+CodeWorkbook | 2天 |
| T2.4 | `GovernanceView` 整合DataCatalog+DatasetExplorer+DQDashboard | 1.5天 |
| T2.5 | `LineageView` 整合DataLineage + 暴露后端REST | 1天 |
| T2.6 | `TasksView` 整合TaskCenter | 1天 |

### Phase 3: 旧路由重定向 + 数据源种子（0.5周）

| Task | 内容 | 工期 |
|------|------|:--:|
| T3.1 | 5个旧路由→新路由重定向 | 0.5天 |
| T3.2 | 数据源种子数据（至少3个连接） | 0.5天 |
| T3.3 | 端到端验证：建管道→commit→回滚→重新部署 | 1天 |

---

## 七、本体工作台vs数据工作台：边界清晰

| 维度 | 本体工作台 | 数据工作台 |
|------|----------|----------|
| 使用角色 | 业务分析师 | 数据工程师 |
| DIKW层 | K（知识层） | D（数据层） |
| 核心操作 | 定义实体/属性/关系/术语 | 连接数据源/建管道/写规则/调度任务 |
| 产出物 | 本体模型（Entity/Property/Relation） | 数据资产（Pipeline/DQRule/Script/Connection） |
| 版本管理 | 无（设计阶段，变化慢） | **Git全量版本化** |
| 与对方关系 | 定义"供应商应该有什么属性" | 实现"供应商数据从哪来、怎么清洗、质量如何" |
| 画布类型 | 实体关系图（ER） | 数据管道DAG + 血缘图 |

**关键交互**：本体工作台中定义的实体，在数据工作台的管道算子中可作为输入/输出Schema引用。

---

## 八、与Ontology Workbench整合经验对比

| | Ontology Workbench | Data Workbench |
|---|---|---|
| 整合前页面数 | 3 (KnowledgeGraph/OntologyDesigner/OntologyExplorer) | 9 |
| 整合后路由 | `/ontology-workbench` | `/data-workbench` |
| 布局 | 三栏（实体树+ER画布+属性编辑器） | 三栏（Git面板+分区画布+资产属性） |
| 核心依赖 | ontology CRUD API | datanet + runtime-core API |
| 独有模块 | 术语库集成、数据映射 | **Git版本面板**、管道执行引擎 |
| 代码量估算 | 8800行 | ~12000行（9页合并+Git面板+管道画布较重） |

---

## 九、下一步行动

1. **立即**：确认runtime-core的Git模块在后端是Spring Bean可注入的（验证 `GitService` 能通过 `@Autowired` 获取）
2. **本周**：出PMO指令，启动Phase 1 — Git Controller + GitPanel
3. **下周**：Phase 2 — 数据工作台布局整合

需要我立即出PMO指令启动Phase 1吗？
