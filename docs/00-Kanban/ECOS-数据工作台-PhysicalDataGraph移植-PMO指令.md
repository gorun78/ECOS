# ECOS 数据工作台 — PhysicalDataGraphPanel 移植指令

> 日期: 2026-07-28 | PMO: ECOS-PMO | 总工时: 3h

## 背景

ceos_new 有两个 ECOS 数据工作台缺失的组件：PhysicalDataGraphPanel（物理数据图谱）和它的依赖 graphLineageCompiler。移植到 ECOS 数据工作台作为新 Tab。

**不需要做的**：
- EnterpriseModelAdaptiveData — 只被 ceos_new 的场景管理模块引用，ECOS 无此模块，不移植
- DataIntegrationView 整体替换 — ECOS 的 DataWorkbenchLayout（3791行/12Tab/真实API）比 ceos_new 的 DataIntegrationView（6511行/mock数据）更完善
- DataEngineService — ECOS 已有真实 API 层 api.ts（341行）

---

## 禁止清单

1. **禁止修改已有Tab的任何代码**——connections/syncs/pipelines/health/lineage/sql-query/engine-config 的Tab内容和路由
2. **禁止新增后端端点**——PhysicalDataGraphPanel 是纯前端可视化组件，无API调用
3. **禁止修改 api.ts / types.ts / Sidebar.tsx / main.tsx / App.tsx**——本次只加Tab，不动路由和侧边栏
4. **禁止删除 DataWorkbenchLayout.tsx 中任何已有代码**——只在末尾追加
5. **禁止引入新的npm依赖**——只用 lucide-react 已有图标

---

## P0 任务

### T1: 移植 PhysicalDataGraphPanel + graphLineageCompiler → ECOS (2h)

**文件清单**：

| 源文件 (ceos_new) | 目标文件 (ECOS) |
|---|---|
| `src/components/AIPWorkbench/PhysicalDataGraphPanel.tsx` (842行) | `src/pages/data-workbench/PhysicalDataGraphPanel.tsx` |
| `src/components/AIPWorkbench/graphLineageCompiler.ts` (178行) | `src/pages/data-workbench/graphLineageCompiler.ts` |

**适配规则**（逐项执行）：

**a. LucideIcon → lucide-react 直接导入**

PhysicalDataGraphPanel.tsx 使用的图标：
- LucideIcon name="Network" → `import { Network } from 'lucide-react'` → `<Network size={...} />`
- LucideIcon name="GitBranch" → `GitBranch`
- LucideIcon name="Database" → `Database`
- LucideIcon name="Shield" → `Shield`
- LucideIcon name="Brain" → `Brain`
- LucideIcon name="Zap" → `Zap`
- LucideIcon name="AlertTriangle" → `AlertTriangle`
- LucideIcon name="Info" → `Info`
- LucideIcon name="RefreshCw" → `RefreshCw`
- LucideIcon name="ZoomIn" → `ZoomIn`
- LucideIcon name="ZoomOut" → `ZoomOut`
- LucideIcon name="Maximize2" → `Maximize2`

替换规则：删除 `import LucideIcon from '../LucideIcon'`，改为从 `lucide-react` 按需导入。`<LucideIcon name="X" size={N} />` → `<X size={N} />`。

**b. showToast prop → 本地函数**

```tsx
// 删除 prop 中的 showToast
// 改为:
function showToast(type: 'success' | 'error' | 'info' | 'warning', msg: string) {
  console.log(`[PhysicalDataGraph] ${type}: ${msg}`);
}
```

**c. Import 路径调整**

```tsx
// graphLineageCompiler 引用
// ceos_new: import { ... } from './graphLineageCompiler';
// ECOS:    import { ... } from './graphLineageCompiler';  (同级目录，不变)
```

**d. 类型引用**

graphLineageCompiler.ts 中引用的 types 和接口自包含，无需外部类型文件。

**e. 删除 ceos_new 特有的父容器依赖**

PhysicalDataGraphPanel 被 MetadataSyncPanel 引用，但作为独立 Tab 不需要 MetadataSyncPanel 的上下文。确认组件可独立渲染。

---

### T2: DataWorkbenchLayout 增加 "物理数据图谱" Tab (1h)

**修改文件**: `/home/guorongxiao/ECOS/ecos_frontend/src/pages/DataWorkbenchLayout.tsx`

**2a. 顶部 import 区追加**（在现有 import 末尾）：

```tsx
import PhysicalDataGraphPanel from './data-workbench/PhysicalDataGraphPanel';
```

**2b. activeTab 类型扩展**（第61行附近，在 Props interface 中）：

在 `activeTab?: 'connections' | ... | 'engine-config'` 的类型联合末尾追加 `| 'physical-graph'`。同样修改 `onActiveTabChange` 的类型。

**2c. Tab 导航中插入按钮**（在第2111行 `</button>` 之后，divider 之前）：

```tsx
            <button
              onClick={() => setActiveTab('physical-graph')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${
                activeTab === 'physical-graph'
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} border-l-2 ${styles.accentBorder} font-extrabold shadow-sm`
                  : `${styles.cardTextMuted} hover:opacity-80`
              }`}
            >
              <GitBranch size={14} className={activeTab === 'physical-graph' ? styles.accentText : styles.cardTextMuted} />
              <span>物理数据图谱</span>
            </button>
```

**2d. 顶部 import 追加 GitBranch**：

```tsx
// 在 lucide-react 的 import 中追加 GitBranch
import { ..., GitBranch } from 'lucide-react';
```

**2e. Tab 内容区追加**（在最后一个 `{activeTab === 'engine-config' && (` 块之后，#3056行附近）：

```tsx
        {activeTab === 'physical-graph' && (
          <PhysicalDataGraphPanel />
        )}
```

---

## 验收

```bash
# 1. TypeScript 编译 — 新增文件零错误
cd /home/guorongxiao/ECOS/ecos_frontend
npx tsc --noEmit 2>&1 | grep -E "PhysicalDataGraphPanel|graphLineageCompiler" | wc -l
# 期望: 0

# 2. 文件存在性
ls -la /home/guorongxiao/ECOS/ecos_frontend/src/pages/data-workbench/PhysicalDataGraphPanel.tsx
ls -la /home/guorongxiao/ECOS/ecos_frontend/src/pages/data-workbench/graphLineageCompiler.ts
# 期望: 两个文件均存在

# 3. LucideIcon 残留检查
grep -c "LucideIcon" /home/guorongxiao/ECOS/ecos_frontend/src/pages/data-workbench/PhysicalDataGraphPanel.tsx
# 期望: 0

# 4. DataWorkbenchLayout 改动量
grep -c "physical-graph" /home/guorongxiao/ECOS/ecos_frontend/src/pages/DataWorkbenchLayout.tsx
# 期望: >= 3 (import + Tab按钮 + 内容区 各一处)

# 5. 前端编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx vite build 2>&1 | tail -5
# 期望: "built in" 成功信息

# 6. Git commit
cd /home/guorongxiao/ECOS/ecos_frontend
git add -A && git commit -m "feat(data-workbench): 移植 PhysicalDataGraphPanel + graphLineageCompiler，新增物理数据图谱Tab"
```

---

## 执行顺序

T1 → 验证1-3 → T2 → 验证4-5 → 验证6 commit
