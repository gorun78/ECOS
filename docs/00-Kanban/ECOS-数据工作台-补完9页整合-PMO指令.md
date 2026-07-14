# PMO指令：数据工作台 — 补完9页整合（骨架→血肉）

> **来源**: 肖总验证发现 — 三栏骨架已完成，但5分区全是占位文字，旧路由未重定向
> **日期**: 2026-07-02
> **铁律**: 单文件粒度。Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。

---

## 当前状态

| 已交付 | 状态 |
|--------|:--:|
| DataWorkbenchLayout 三栏布局 | ✅ |
| 5分区导航（图标+高亮切换） | ✅ |
| 右侧面板占位 | ✅ |
| TS零错误 | ✅ |
| **旧路由重定向** | ❌ 一个都没做 |
| **5分区内容** | ❌ 全是 `<SectionPlaceholder>` |

---

## 禁止清单

1. 不碰GitPanel（已嵌入，保留不动）
2. 不新建后端API
3. 不修改旧页面的核心业务逻辑——搬迁不改逻辑
4. 不删除旧页面文件

---

## T0: 旧路由重定向（15分钟）

**改文件**: `c2eos/src/main.tsx`，第77-82行 + 第97-102行

**把9个旧路由全部改成重定向**：

```tsx
// 改前（第77-82行）：
<Route path="catalog" element={<DataCatalog />} />
<Route path="dataset_explorer/:assetId?" element={<DatasetExplorer />} />
<Route path="lineage" element={<DataLineage />} />
<Route path="dq_dashboard" element={<DataQualityDashboard />} />
<Route path="datalake" element={<DataLake />} />

// 改后：
<Route path="catalog" element={<Navigate to="/data-workbench" replace />} />
<Route path="dataset_explorer/:assetId?" element={<Navigate to="/data-workbench" replace />} />
<Route path="lineage" element={<Navigate to="/data-workbench" replace />} />
<Route path="dq_dashboard" element={<Navigate to="/data-workbench" replace />} />
<Route path="datalake" element={<Navigate to="/data-workbench" replace />} />
```

```tsx
// 改前（第97行+101-102行）：
<Route path="datasources" element={<DataSourceManager />} />
<Route path="pipeline" element={<PipelineBuilder />} />
<Route path="workbook" element={<CodeWorkbook />} />
<Route path="task_center" element={<TaskCenter />} />

// 改后：
<Route path="datasources" element={<Navigate to="/data-workbench" replace />} />
<Route path="pipeline" element={<Navigate to="/data-workbench" replace />} />
<Route path="workbook" element={<Navigate to="/data-workbench" replace />} />
<Route path="task_center" element={<Navigate to="/data-workbench" replace />} />
```

**验收**: 浏览器访问旧路由 `/catalog` → 跳转到 `/data-workbench`。9个旧路由全部验证。

---

## T1: 5个分区View — 填充真实内容

**当前**：DataWorkbenchLayout.tsx 的 `<SectionPlaceholder>` 只显示标题+描述文字。
**目标**：每个分区渲染从旧页面搬迁过来的真实UI。

**实现策略**：不改DataWorkbenchLayout的三栏骨架。把第112行的 `<SectionPlaceholder section={activeSection} />` 替换为一个Switch，每个case渲染从旧页面提取的核心内容组件。

### 改造 DataWorkbenchLayout.tsx

**改文件**: `c2eos/src/pages/DataWorkbenchLayout.tsx`

**第112行** — 替换占位：

```tsx
// 删掉这行：
<SectionPlaceholder section={activeSection} />

// 替换为：
{activeSection === 'datasource'  && <SourcesContent />}
{activeSection === 'pipeline'    && <PipelineContent />}
{activeSection === 'governance'  && <GovernanceContent />}
{activeSection === 'lineage'     && <LineageContent />}
{activeSection === 'schedule'    && <TasksContent />}
```

**然后在该文件中新增5个内容组件**（每个50-100行），直接复用旧页面的核心渲染逻辑：

### T1.1 SourcesContent（数据源管理）

**搬迁来源**: `DataSourceManager.tsx`

**搬迁内容**：
- 数据源列表表格（名称/类型/状态/最近同步时间）
- [添加数据源]按钮 → 弹出DataSourceManager原有的表单Modal
- 测试连接按钮

**关键**：直接 `import { DataSourceList } from '../pages/DataSourceManager'` 或把DataSourceManager的核心渲染逻辑拷贝过来。**不改DataSourceManager.tsx原文件**。

---

### T1.2 PipelineContent（数据管道）

**搬迁来源**: `PipelineBuilder.tsx`

**搬迁内容**：
- 管道列表（至少显示管道名称+状态）
- [新建管道]按钮
- 管道画布（PipelineBuilder中已有的ReactFlow画布，直接import其组件）

**关键**：PipelineBuilder如果已拆分为独立组件（如 `<PipelineCanvas />`），直接import。如果没有，把PipelineBuilder的核心JSX拷贝过来。

---

### T1.3 GovernanceContent（数据治理）

**搬迁来源**: `DataQualityDashboard.tsx` + `DataCatalog.tsx`

**搬迁内容**：
- 质量规则数量 + 问题数量卡片（从DQDashboard取）
- 资产目录树（从DataCatalog取，取前两层即可）
- 健康评分仪表盘（从DQDashboard取核心图表）

---

### T1.4 LineageContent（数据血缘）

**搬迁来源**: `DataLineage.tsx`

**搬迁内容**：
- 血缘图ReactFlow画布（DataLineage的核心渲染逻辑）
- 搜索框（输入对象名查看血缘）
- 如果DataLineage已有独立 `<LineageGraph>` 组件，直接import

---

### T1.5 TasksContent（任务调度）

**搬迁来源**: `TaskCenter.tsx`

**搬迁内容**：
- 任务列表（名称/状态/最近执行时间）
- 执行历史表格

---

## 验收标准

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | 访问 `/catalog` → 跳转 `/data-workbench` | 浏览器地址栏 |
| 2 | 访问 `/pipeline` → 跳转 `/data-workbench` | 浏览器地址栏 |
| 3 | 访问其他7个旧路由 → 全部跳转 | 逐个验证 |
| 4 | 数据源分区显示≥1个数据源 | 浏览器截图 |
| 5 | 管道分区显示管道列表 | 浏览器截图 |
| 6 | 治理分区显示质量卡片 | 浏览器截图 |
| 7 | 血缘分区显示ReactFlow画布 | 浏览器截图 |
| 8 | 调度分区显示任务列表 | 浏览器截图 |
| 9 | 零TS错误 | `npx tsc --noEmit | grep DataWorkbench` = 0 |

---

## 执行顺序

```
T0 旧路由重定向（15分钟）— 10行改动
T1.1 SourcesContent — 最简单，先做
T1.2 PipelineContent — 最重，
T1.3 GovernanceContent
T1.4 LineageContent
T1.5 TasksContent
全部改同一个文件 DataWorkbenchLayout.tsx
```

**总工时**: ~3.5天。

---

**一句话**: 三栏骨架不用动。把 `SectionPlaceholder` 替换成5个真实内容组件——每个组件从旧页面搬核心渲染逻辑过来，不改原文件。旧路由9个加重定向。GitPanel保留不动。
