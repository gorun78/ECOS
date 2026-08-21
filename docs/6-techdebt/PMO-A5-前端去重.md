# PMO-A5: 前端去重（AsyncTaskCenterView + 权威版本确认）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-FE
> **铁律**: ①每个组件只留一份权威版本 ②统一 import 指向权威版本，删重复文件 ③`npx tsc --noEmit` 零新增错误 ④每 Task 独立 commit

## §背景

前端存在同组件多副本。已核实首例：`AsyncTaskCenterView.tsx` 有两份（`pages/` 1137 行 / `components/` 1138 行），差异仅在图标实现方式（`pages` 用 `LucideIcon` wrapper 组件，`components` 用 `lucide-react` 直接组件）。两份都在被 import：`main.tsx`（路由 `engine-tasks`）用 pages 版，`Sidebar.tsx` 用 components 版。

诊断报告另指 18 处需确认权威版本（ObjectExplorerView/Sidebar 等）。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 统一 AsyncTaskCenterView：删 `components/AsyncTaskCenterView.tsx`，改 `Sidebar.tsx` 的 import 从 `./AsyncTaskCenterView` 改为 `../pages/AsyncTaskCenterView` | 编译通过，engine-tasks 路由 + 侧边栏均正常 |
| T2 | 全量扫出 pages/ 与 components/ 两目录下的同名 `.tsx`，逐个 diff + 追 import 确认权威版本，统一 import、删副本 | 无同名残留 |
| T3 | `npx tsc --noEmit` | 零新增错误 |

### T1 详细（权威判断）

- **权威 = pages 版**：`main.tsx` 路由 `engine-tasks` 是主入口，pages 版是"页面"正确归属。
- **改 import**：`Sidebar.tsx` 第 10 行 `import AsyncTaskCenterView from "./AsyncTaskCenterView";` → `import AsyncTaskCenterView from "../pages/AsyncTaskCenterView";`
- **删副本**：`git rm src/components/AsyncTaskCenterView.tsx`

### T2 方法（18 处权威确认的通用步骤）

```bash
cd /home/guorongxiao/ECOS/ecos_frontend/src
# 1. 列出 pages/ 和 components/ 同名文件
comm -12 <(ls pages/ | sort) <(ls components/ | sort)
# 2. 对每个同名文件：diff 差异 → 追谁 import 它 → 定权威 → 统一 import → 删副本
# 3. 权威判断规则：main.tsx 路由入口 > Sidebar/布局引用 > 仅被单处引用
```

**已知需处理项（除 AsyncTaskCenterView 外）**：
- `LucideIcon` 也有两份：`components/LucideIcon` vs `pages/data-workbench/LucideIcon`（AsyncTaskCenterView 的两个版本分别 import 了不同的 LucideIcon，T1 统一后需连带确认 LucideIcon 权威）

## §禁止清单

1. ❌ 不做组件功能改动（纯去重 + 改 import 路径，不改组件内部逻辑）
2. ❌ 不删仍被 import 的权威版本（删前必须 `grep -rn "import.*<组件名>" src/` 确认无引用）
3. ❌ 不跳过 tsc 验证
4. ❌ 不改路由 path（`engine-tasks` 等路由路径保持）

## §验证门禁

```bash
# V1: 无同名残留（以 AsyncTaskCenterView 为例）
ls /home/guorongxiao/ECOS/ecos_frontend/src/components/AsyncTaskCenterView.tsx 2>/dev/null && echo "残留" || echo "已删"

# V2: 无引用指向已删副本
grep -rn "components/AsyncTaskCenterView" /home/guorongxiao/ECOS/ecos_frontend/src/ || echo "无残留引用"

# V3: TypeScript 编译零新增错误
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit
# 期望: 零新增错误（对比修复前基线）
```

## §工时

1-2 天（T1 首例 0.5 天 + T2 全量 18 处逐个 diff/追 import 1-1.5 天）。

## §风险

- **import 路径相对性**：`Sidebar.tsx` 在 `components/` 目录，改 import 到 `../pages/AsyncTaskCenterView` 注意相对路径正确（`components/` → 上跳一级 → `pages/`）。
- **图标实现差异**：pages 版依赖 `components/LucideIcon`（wrapper），components 版依赖 `lucide-react` 直接组件。统一到 pages 版后，若 pages 版的 LucideIcon wrapper 缺某些图标名，会导致图标不显示——需在浏览器验证。
- **18 处不全在 pages/components**：诊断报告的 18 处可能涉及其他目录（如 `pages/data-workbench/` 等），T2 的 `comm` 只覆盖 pages vs components，需用 `search_files` 全量搜同名 `.tsx` 兜底。
