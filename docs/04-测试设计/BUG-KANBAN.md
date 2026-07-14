# BUG-KANBAN: ECOS看板管理平台 缺陷报告

> 最后更新：2026-06-16 | 看板根任务: t_59535075

---

## 🔴 P0 — 阻塞发布

### BUG-001: currentUserRole 硬编码为 'admin' ✅ 已修复

| 项目 | 内容 |
|------|------|
| **严重程度** | 🔴 **P0 — 阻塞发布** |
| **模块** | 前端 / store/board.ts |
| **状态** | ✅ **已在 t_59535075 中修复** |
| **修复人** | Hermes Agent (root task) |
| **涉及PRD** | US-006 看板权限控制 |

**修复内容**：将 `currentUserRole` computed property 从硬编码 `return 'admin'` 改为：
1. 从 `useAuthStore()` 获取当前用户ID
2. 在 `currentBoard.members` 中查找对应用户角色
3. 未找到时返回 `'viewer'` 最低权限

**验证**: vue-tsc --noEmit 0 error, vite build 3.91s 构建成功。

---

### BUG-005: 后端代码未持久化到磁盘 ❌ 未修复

| 项目 | 内容 |
|------|------|
| **严重程度** | 🔴 **P0 — 阻塞发布** |
| **模块** | 后端 / ecos-kanban |
| **状态** | ⏳ 已创建子任务 t_99fd7603 |
| **影响** | 无法编译后端、无法启动服务、无法做E2E测试 |

**根因**: 上一轮生成的70个Java文件、Flyway迁移SQL、POM配置未写入磁盘。

---

## 🟡 P1 — 重要

### BUG-002: 标签/评论/子任务仅本地内存操作 ❌ 未修复

| 项目 | 内容 |
|------|------|
| **严重程度** | 🟡 **P1** |
| **模块** | 前端 / CardDetailDrawer.vue |
| **状态** | ⏳ 已创建子任务 t_697a41aa |

**代码位置**: CardDetailDrawer.vue:236-283
- `removeTag` — 直接修改 `props.card.tags`
- `submitTag` — 直接 push 到 `props.card.tags`
- `addChecklist` — 直接 push
- `removeChecklist` — 直接 filter
- `submitComment` — 直接 push

---

### BUG-003: isDoneColumn 逻辑未在拖拽时强制执行 ❌ 未修复

| 项目 | 内容 |
|------|------|
| **严重程度** | 🟡 **P1** |
| **模块** | 前端 / BoardView.vue |
| **状态** | ⏳ 已创建子任务 t_697a41aa |

**问题**: 卡片拖入 `isDoneColumn=true` 的列时，`completedAt` 未自动设置。

---

### BUG-004: WIP 检查不一致 ❌ 未修复

| 项目 | 内容 |
|------|------|
| **严重程度** | 🟡 **P1** |
| **模块** | 前端 / BoardView.vue |
| **状态** | ⏳ 已创建子任务 t_697a41aa |

**问题**: WIP限制检查在部分位置使用 `>=`，部分使用 `>`，行为不一致。

---

## 🟢 P2 — 建议项

### BUG-006: 未使用的 vuedraggable 依赖

| 项目 | 内容 |
|------|------|
| **严重程度** | 🟢 **P2** |
| **模块** | 前端 / package.json |

**问题**: 项目使用原生 HTML5 Drag & Drop API（BoardView.vue），但依赖中仍包含 `vuedraggable`。建议移除。

---

## 总体状态

| 严重程度 | 总数 | 已修复 | 待修复 |
|:--------:|:----:|:------:|:------:|
| 🔴 P0 | 2 | 1 | 1 (t_99fd7603) |
| 🟡 P1 | 3 | 0 | 3 (t_697a41aa) |
| 🟢 P2 | 1 | 0 | 1 |
