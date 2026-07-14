# ECOS 移动端适配设计规范

> 版本: v1.0 | 日期: 2026-06-20 | 作者: ECOS-PMO

## 一、现状审计

| 维度 | 状态 | 说明 |
|------|------|------|
| viewport meta | ✅ | index.html 已有 `<meta name="viewport">` |
| Tailwind 版本 | ✅ | v4.1.14 + `@tailwindcss/vite` |
| 响应式类使用 | ❌ | 仅 14/37 文件使用了 sm:/md:/lg: 断点 (61 处) |
| 移动端菜单 | ❌ | 无 hamburger/sheet/drawer，Sidebar 固定 240px |
| 固定宽度 | ❌ | 21 处 `w-[300+]` 固定宽度 |
| 移动端检测 | ❌ | 零 `useMediaQuery`/`isMobile` 代码 |

## 二、设计原则

1. **渐进增强 (Progressive Enhancement)**: 不改动桌面端现有布局，优先通过 Tailwind 响应式类叠加移动端样式
2. **Mobile-First Breakpoints**: `sm:640px` `md:768px` `lg:1024px`
3. **组件级适配**: 每个页面独立响应式改造，互不阻塞
4. **触控友好**: 最小触控目标 44px，间距充足

## 三、全局骨架改造

### 3.1 Sidebar → 可折叠抽屉

```
桌面端 (≥md):  固定侧边栏，240px 宽
移动端 (<md):  隐藏侧边栏，hamburger 按钮触发 overlay drawer
```

**实现方案**:
- 新增 `useMobileSidebar` hook，内部用 `window.matchMedia('(max-width: 767px)')` 检测
- Topbar 左侧新增 hamburger 按钮 (`<Menu>` icon)，仅在移动端显示
- Sidebar 组件接收 `collapsed` prop，移动端变为绝对定位 overlay + backdrop
- 点击导航项 → 自动关闭 drawer

### 3.2 Topbar 简化

- 移动端：隐藏多 Tab 标签栏，仅显示 hamburger + 标题 + 快捷操作
- 桌面端：保持现有 Topbar 不变

## 四、页面分批改造

### 批次 1 (P0 — 用户最常用)

| 文件 | 改造点 |
|------|--------|
| `BizDashboard.tsx` | 统计卡片 `grid-cols-4` → `grid-cols-2 sm:grid-cols-4`；表格横向滚动 |
| `ObjectExplorer.tsx` | 列表/详情 → 移动端堆叠；表单全宽 |
| `OntologyDesigner.tsx` | 画布缩小适配；属性面板堆叠 |
| `DataCatalog.tsx` | 表格 `overflow-x-auto`；筛选器折叠 |

### 批次 2 (P1 — 数据层+知识层)

| 文件 | 改造点 |
|------|--------|
| `DatasetExplorer.tsx` | 统计卡片响应式；SQL 编辑器全宽 |
| `DataQualityDashboard.tsx` | 规则卡片 2 列；表格横向滚动 |
| `WorkflowDesigner.tsx` | DAG 画布缩放；节点面板堆叠 |
| `WorldModelViewer.tsx` | 目标卡片 `grid-cols-3` → `grid-cols-1 sm:grid-cols-2`；因果图缩放 |
| `AgentStudio.tsx` | 配置表单堆叠；聊天面板全高 |
| `AgentMesh.tsx` | Agent 卡片 1 列 |

### 批次 3 (P2 — 其他)

| 文件 | 改造点 |
|------|--------|
| 其余 10+ 页面 | 标准响应式布局：`w-full max-w-{size} mx-auto`；内边距 `px-4 sm:px-6 lg:px-8` |

## 五、通用响应式模式

```tsx
// 页面容器
<div className="p-4 sm:p-6 lg:p-8 max-w-7xl mx-auto">

// 网格自适应
<div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">

// 表格容器
<div className="overflow-x-auto -mx-4 sm:mx-0">
  <table className="min-w-full">

// 移动端隐藏
<div className="hidden md:block">

// 移动端显示
<div className="block md:hidden">
```

## 六、技术约束

- 使用 Tailwind CSS 响应式类，不允许新增 CSS 文件
- 不引入新依赖（如 `react-responsive` 等）
- `useMediaQuery` 用原生 `window.matchMedia` 实现
- 所有改动在 `/home/guorongxiao/c2eos/src/` 下
- TypeScript 编译零错误 (`npx tsc --noEmit --skipLibCheck`)
