# PMO指令：Phase2-1-UX补齐 — 前端交互缺陷补齐

> 来源: 完善计划 Phase 2-1 T3 | 工期: 1周 | 范围: 前端 aiworkbench/ 9页面 | 依赖: PMO-09文件拆分 | 并行: 可与PMO-10(国际化)并行

---

## §背景

AI工作台9页面审计发现大量基础交互缺失：排序0%覆盖、加载状态缺4/9页、分页缺6/9页、确认弹窗缺5/9页。

**缺陷数排名**：

| 页面 | 缺陷数 | 严重度 |
|------|:--:|:--:|
| DashboardView | 11 | 🔴 |
| ModelCatalogView | 9 | 🔴 |
| CognitiveOperatingSystem | 8 | 🔴 |
| AgentTestConsole | 8 | 🔴 |
| GuardrailsView | 6 | 🟡 |
| LogicView | 4 | 🟡 |
| 其他3页 | 2 | 🟢 |

---

## §禁止清单

1. ❌ 不改动已拆分组件中由PMO-09引入的props接口（只增交互不换接口）
2. ❌ 不改动PMO-10已迁移的i18n key（新增key走aiworkbench.common命名空间）
3. ❌ 通用组件复用 `components/common/` 中的已有实现
4. ❌ 通用组件优先复用：LoadingSkeleton/EmptyState/ConfirmDialog（如存在则用已有，如不存在则新建）

---

## §通用组件（第1天，先行交付）

先检查 `components/common/` 目录，若不存在则新建：

### UX-0a: LoadingSkeleton

**文件**：`components/common/LoadingSkeleton.tsx`（如不存在）

**功能**：
- 接收 `rows?: number` 参数（默认5行）
- 渲染灰色脉冲动画骨架屏
- 适用于表格/卡片两种模式

### UX-0b: EmptyState

**文件**：`components/common/EmptyState.tsx`（如不存在）

**功能**：
- 接收 `icon?/title/description/action?` 参数
- 渲染居中的空状态插图+文字+可选操作按钮
- 默认icon为 Inbox（lucide-react）

### UX-0c: ConfirmDialog

**文件**：`components/common/ConfirmDialog.tsx`（如不存在）

**功能**：
- 接收 `open/onConfirm/onCancel/title/message/confirmText/cancelText/variant?` 参数
- variant: 'danger'(红色) / 'warning'(橙色) / 'info'(蓝色)
- 支持键盘Esc关闭

---

## §页面补齐（第2-5天）

### UX-1: 加载骨架屏补齐（4页面）— 1.5天

| 页面 | 修改 |
|------|------|
| AgentStudioView | 数据fetch期间显示 `<LoadingSkeleton rows={5} />` |
| LogicView | 同上 |
| DashboardView | 数据fetch期间显示 `<LoadingSkeleton rows={4} />` |
| ModelCatalogView | 数据fetch期间显示 `<LoadingSkeleton rows={3} />` |

### UX-2: 空状态补齐（4页面）— 1天

| 页面 | 修改 |
|------|------|
| ChatbotStudioView | 无Agent选中时显示 `<EmptyState title="选择Agent" description="从左侧列表选择一个Agent开始对话" />` |
| DashboardView | 无数据时显示 `<EmptyState title="暂无数据" description="系统运行后将自动采集指标" />` |
| ModelCatalogView | 无模型时显示 `<EmptyState title="暂无模型" description="联系管理员添加模型" action="添加模型" />` |
| AgentTestConsole | 未执行测试时显示 `<EmptyState title="开始测试" description="选择Agent和问题后开始评估" />` |

### UX-3: 分页器补齐（6页面）— 1.5天

**实现**：复用已有的Pagination组件（检查 `components/common/Pagination.tsx`），如不存在则按以下规范新建：

```tsx
interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
}
```

| 页面 | 修改 |
|------|------|
| KnowledgeView | 各子Tab列表加 `<Pagination>` |
| GuardrailsView | GuardrailPolicyList 加 `<Pagination>` |
| LogicView | Pipeline列表加 `<Pagination>` |
| ModelCatalogView | 模型列表加 `<Pagination>` |
| CognitiveOperatingSystem | 如有列表数据加 `<Pagination>` |
| AgentTestConsole | 测试历史列表加 `<Pagination>` |

### UX-4: ConfirmDialog补齐（5页面）— 1天

**统一rule**：删除/下线/重置等危险操作，点击后弹出ConfirmDialog，确认后才执行。

| 页面 | 操作 | 触发条件 |
|------|------|------|
| GuardrailsView | 删除策略 | 点击删除按钮 → ConfirmDialog(variant='danger') |
| DashboardView | 重置指标 | 点击重置按钮 → ConfirmDialog(variant='warning') |
| ModelCatalogView | 删除模型 | 点击删除按钮 → ConfirmDialog(variant='danger') |
| CognitiveOperatingSystem | 如有操作 | 危险操作 → ConfirmDialog |
| AgentTestConsole | 清空历史 | 点击清空 → ConfirmDialog(variant='warning') |

### UX-5: Table排序（9页面）— 1天

给所有列表页加列排序功能。

**实现方式**：如已有通用Table组件则加排序props（`sortBy/sortOrder/onSort`），否则各页面单独实现。

**覆盖**：AgentStudioView/KnowledgeView/GuardrailsView/ChatbotStudioView(Agent列表)/LogicView/ModelCatalogView/CognitiveOperatingSystem/AgentTestConsole/DashboardView
---

## §验收

```bash
# 1. 通用组件存在
ls src/components/common/LoadingSkeleton.tsx src/components/common/EmptyState.tsx src/components/common/ConfirmDialog.tsx 2>/dev/null

# 2. 编译无新增错误
cd ecos_frontend && npx tsc --noEmit 2>&1 | wc -l  # ≤ 289

# 3. 浏览器逐页面验证
# - 网络调为Slow 3G → 确认每页显示骨架屏
# - 首次进入空数据页 → 确认显示EmptyState
# - 每页列表 → 确认分页器可用
# - 每页危险操作 → 确认弹出ConfirmDialog，取消不执行，确认后执行
# - 每页列表 → 确认列头可点击排序
```
