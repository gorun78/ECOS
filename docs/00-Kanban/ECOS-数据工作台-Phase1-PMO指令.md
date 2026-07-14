# PMO指令：数据工作台 Phase 1 — Git 基础设施 + 布局骨架

> **来源**: `/home/guorongxiao/ECOS/03-设计文档/data-workbench-design.md`
> **日期**: 2026-07-02
> **范围**: Phase 1（Git Controller + GitPanel + 布局容器）
> **铁律**: 单文件粒度，DONE=commit+curl。后端5不碰。

---

## 一、现状

| 维度 | 状态 |
|------|------|
| 前端9个独立页面 | ✅ 全部存在（DataSourceManager/DataLake/PipelineBuilder/CodeWorkbook/DataCatalog/DatasetExplorer/DQDashboard/DataLineage/TaskCenter） |
| 路由 | ✅ main.tsx 中全部活跃 |
| 后端 Git 模块 | ✅ runtime-core/git/ + sysman-api/git/ 双模块（GitService/GitHistoryService均已实现） |
| 后端缺失 | ❌ 无 Git REST Controller — 需新建暴露 Git API |

---

## 二、Phase 1 任务分解

### T1: 后端 GitController（2h）

**目标**: 新增 Controller 暴露 Git REST API，包装现有 GitService。

**文件**: `databridge-gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/GitController.java`（新建）

**端点**:
```
GET    /api/v1/ecos/git/status              → 仓库状态（分支/文件变更）
GET    /api/v1/ecos/git/commits?path=       → 提交历史
GET    /api/v1/ecos/git/diff?from=&to=&path= → 版本差异
POST   /api/v1/ecos/git/commit              → 提交 {message, files[]}
POST   /api/v1/ecos/git/tag                 → 打标签 {name, message}
POST   /api/v1/ecos/git/rollback            → 回滚 {commitHash, path}
GET    /api/v1/ecos/git/branches            → 分支列表
POST   /api/v1/ecos/git/branch              → 创建/切换分支 {name, action}
```

**验收**: curl 全部端点返回 200。

---

### T2: 前端 GitPanel 组件（1.5h）

**目标**: 左侧常驻 Git 面板，调用 T1 的 API。

**文件**: `src/components/data-workbench/GitPanel.tsx`（新建）

**功能**:
- BranchSelector：下拉切换分支
- UncommittedChangesList：未提交的文件列表
- CommitHistoryList：最近10条提交
- GitActionBar：commit / push / pull / tag 按钮

**验收**: 页面渲染正常，API 调用返回数据。

---

### T3: 前端布局骨架（1h）

**目标**: 建立 `/data-workbench` 路由 + 三栏布局容器。

**文件**:
- `src/pages/DataWorkbenchLayout.tsx`（新建）
- `src/main.tsx`（修改：加路由）

**布局**: 左280px(GitPanel+分区导航) / 中间flex(5分区切换) / 右320px(属性面板)

**验收**: 页面可访问，三栏可见，分区导航可点击。

---

## 三、委托方案

| Agent | 任务 | 文件 |
|-------|------|------|
| ECOS-BE | T1 GitController | 1个新建Java文件 |
| ECOS-FE | T2 GitPanel | 1个新建TSX文件 |
| ECOS-FE | T3 布局骨架 | 1新建+1修改 |

**并行执行**：T1和T2/T3无依赖，可同时开工。

---

## 四、后端约束（必读）

- GitService 在 runtime-core 模块，需通过 @Autowired 注入
- 不新建模块、不修改已有 API 签名
- Gateway 的 GitController 只能调用 runtime-core 暴露的接口
- 如 runtime-core 的 GitService 是 interface 且无 Spring Bean → 需在 sysman-impl 用 JdbcTemplate 直查 git_commit 表作为回退方案
