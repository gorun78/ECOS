# PMO指令: C1 前端大文件拆分（WorkshopView + DictManager）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-FE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①拆分后单文件 < 400 行 ②UI 零退化（不改任何可见行为）③不改路由路径、不改组件对外 props ④禁止跨 Task 预创建文件

## 零、现状摸底（已核实）

| 文件 | 路径 | 行数 |
|------|------|------|
| WorkshopView | `ecos_frontend/src/pages/WorkshopView.tsx` | 2,243 行 |
| DictManager | `ecos_frontend/src/pages/DictManager.tsx` | 1,606 行 |

均为 main.tsx 路由直接渲染的主页面，拆分时组件目录就近建（`src/pages/workshop/`、`src/pages/dict/` 或同目录 co-located，按现有项目约定）。

## 一、目标状态

两个大文件拆成「主入口 + 职责单一的子组件」，每个文件 < 400 行，对外行为零变化。

## 二、分阶段执行计划

| Task | 文件 | 操作 |
|:-----|------|------|
| P1-1 | `WorkshopView.tsx`（2,243 行） | 拆分为 WorkshopView（主入口，负责布局+状态编排）+ 子组件（参考拆分维度：WidgetRenderer 组件渲染、VariableManager 变量面板、PageTabs 页签容器、以及按实际 JSX 结构拆出的 Toolbar/Inspector/Canvas 等）。**拆分维度以实际代码结构为准，参考名可调整** |
| P1-2 | `DictManager.tsx`（1,606 行） | 拆分为 DictManager（主入口）+ 子组件（参考：DictTypeList 字典类型列表、DictItemTable 字典项表格、DictItemForm 编辑表单/抽屉、以及导入导出面板）。同 P1-1 原则 |

**实现顺序**：P1-1 → P1-2（互不依赖，可并行，但建议逐个拆逐个验证，避免一次动两个大文件难定位回归）。

## 三、禁止清单

- ❌ 拆分时顺手改业务逻辑、改样式、改文案（这是「拆分」不是「重构」）
- ❌ 改路由 path（`workshop` / `dict` 路由不变）
- ❌ 改组件对外 export 签名（WorkshopView/DictManager 仍是 default export 的页面组件）
- ❌ 引入新依赖 / 新状态管理库
- ❌ 超过 400 行还「拆不动」就放弃——必须拆到底

## 四、风险与回滚

- **状态提升风险**：拆子组件时把 state 提到主入口，注意保持原 state 的生命周期与更新时序，避免子组件挂载/卸载导致状态丢失。
- **回滚**：每个文件拆分单独 commit，`git revert` 即可。

## 五、验证门禁

```bash
# V1: TypeScript 编译零新增错误
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | head -30
# 期望: 无新增错误（拆分前的既有错误不算，拆分引入的算）

# V2: 单文件行数门禁
wc -l src/pages/WorkshopView.tsx src/pages/DictManager.tsx
# 期望: 均 < 400 行；拆分出的子组件每个也 < 400 行

# V3: 无残留旧结构（原文件内的子功能已迁出）
grep -n "function\|const .* = (" src/pages/WorkshopView.tsx | wc -l
# 期望: 主入口只剩编排逻辑，子功能函数已迁到子组件
```

## 六、工时估算

P1-1（4h）+ P1-2（3h）≈ **7h**
