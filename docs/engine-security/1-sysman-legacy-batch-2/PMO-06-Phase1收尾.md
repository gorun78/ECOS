# PMO指令：Phase1-sysman-06 — Phase 1 收尾

> **来源**: 差距分析复盘 | **工期**: 3天 | **范围**: 前端为主 | **依赖**: PMO-01~05 全部完成

---

## §背景

Phase 1 的5个PMO已完成核心目标（登录安全+内置角色+安全中心+安全接入规则），差距分析复盘发现12项遗留，分为三类：拆分未执行、功能未闭环、体验未打磨。

---

## §禁止清单

1. ❌ 不改后端Java（仅前端工作）
2. ❌ 不改路由结构（不新增/删除HashRouter路由）
3. ❌ 不删除TenantManager.tsx（只移除侧边栏入口，文件保留）
4. ❌ UserManagement拆分后的子组件放在 `pages/user-management/` 目录下，不要散放在 `pages/` 根目录
5. ❌ 国际化key不重复：`user.xxx`（用户管理）、`config.xxx`（系统配置）、`audit.xxx`（审计）、`common.xxx`（通用）

---

## §Task

### P0: 拆分未执行（2项，1天）

#### T1: UserManagement.tsx 拆分

**现状**: 1393行单文件，含用户CRUD/筛选/角色绑定/创建表单混在一起。

**拆分到 `pages/user-management/`**：

| 文件 | 行数目标 | 职责 |
|------|:--:|------|
| `UserList.tsx` | ~400 | 表格 + 筛选栏 + 分页 + CSV导入导出按钮（不包含编辑弹窗） |
| `UserEditModal.tsx` | ~300 | 新建/编辑用户的Modal表单（含密码设置/角色绑定） |
| `UserRoleBinding.tsx` | ~250 | 角色绑定面板（用户→角色多选） |
| `UserFilter.tsx` | ~150 | 筛选栏：搜索框+状态下拉+角色下拉+搜索按钮 |

**UserManagement.tsx 保留**：作为组合入口（~200行），组装上述4个子组件，对外暴露相同的props接口。

**铁律**：
- 用 `useCallback` 包裹传递给子组件的事件处理函数
- 所有API调用保持原路径不变
- CSV导入导出按钮留在UserList中，调原逻辑

---

#### T2: 移除租户管理侧边栏入口

**操作**：从 `App.tsx` 侧边栏的"系统管理"分组中移除"租户管理"入口。

**保留**：
- `pages/TenantManager.tsx` 文件不删
- 路由 `/#/tenant` 不删（只是侧边栏不显示入口）

---

### P1: 功能未闭环（3项，1天）

#### T3: 用户详情抽屉

**现状**：点击用户行无反应。

**实现**：点击用户行 → 右侧滑出抽屉(Drawer)，含4个区块：

| 区块 | 内容 |
|------|------|
| 基本信息 | 用户名/姓名/邮箱/手机/状态/创建时间 |
| 角色 | 当前角色列表（badge形式） |
| 安全Profile | 登录失败次数/锁定状态/上次登录时间/IP |
| 最近操作 | 最近10条该用户的操作审计日志（调 `/api/v1/audit` ） |

**组件**：`pages/user-management/UserDetailDrawer.tsx`（~250行）

**交互**：
- 点击表格行→打开抽屉
- 点击抽屉外/关闭按钮→关闭
- 从API获取完整用户信息（含安全profile和操作日志）

---

#### T4: 批量操作

**实现**：用户列表支持批量操作。

| 元素 | 说明 |
|------|------|
| 复选框列 | 表头全选+每行checkbox |
| 批量工具栏 | 选中≥1条时悬浮显示："已选N项 [启用] [禁用] [删除]" |
| 确认弹窗 | 批量删除前弹出确认"确定删除N个用户？此操作不可撤销" |

**组件**：集成到 `UserList.tsx` 中（~100行增量）

---

#### T5: 密码强度指示器

**实现**：UserEditModal中密码输入框下方显示密码强度条。

| 强度 | 条件 | 颜色 |
|------|------|------|
| 弱 | < 8字符 | 红色 |
| 中 | ≥8字符 + 含数字/大写/特殊字符中任意两种 | 黄色 |
| 强 | ≥8字符 + 含数字+大写+特殊字符全部 | 绿色 |

**实现方式**：纯前端实时计算，不需要调后端。

---

### P2: 体验打磨（7项，1天）

#### T6: 侧边栏i18n去硬编码

**现状**：侧边栏按钮的label仍是中文硬编码。

**修复**：`App.tsx` 中所有 `label="中文名 描述"` 改为 `label={t('nav.xxx') + ' ' + t('nav.xxxDesc')}`。

在 `locales/zh-CN.json` 补充缺失的key，`locales/en.json` 对应英文。

---

#### T7: 骨架屏

**实现**：用户列表和审计列表首次加载时显示骨架屏(3行占位)，数据加载完成后切换为真实表格。

**组件**：`components/common/SkeletonTable.tsx`，复用即可。

---

#### T8: Toast通知

**实现**：操作成功/失败用toast通知。

| 场景 | 消息示例 | 类型 |
|------|------|------|
| 创建用户成功 | "用户 xxx 创建成功" | success(绿色) |
| 删除用户 | "用户 xxx 已删除" | success |
| 操作失败 | "操作失败：网络错误" | error(红色) |

**实现方式**：`components/common/Toast.tsx` + ToastContext。用 `react-hot-toast` 或手写。已有`useToast`则复用。

---

#### T9: 确认弹窗

**实现**：危险操作(删除/下线/重置密码)前弹出确认对话框。

```tsx
<ConfirmDialog
  title="删除用户"
  message="确定删除用户 admin？此操作不可撤销"
  confirmText="删除"
  onConfirm={handleDelete}
/>
```

**组件**：`components/common/ConfirmDialog.tsx`

**覆盖场景**：用户删除、用户下线、密码重置、批量删除。

---

#### T10: 响应式验证

**验证**（不是开发）：调整浏览器窗口到1366x768，确认安全中心三个Tab布局不溢出、表格横向可滚动、侧边栏可收起。

**如发现溢出**：修复溢出元素的CSS（用`overflow-x-auto`或调整列宽）。

---

#### T11: 样式一致性

**验证**（不是开发）：确认安全中心页面使用 `useTheme()` 提供的token（如 `theme.colors.primary`）而非Tailwind硬编码色值（`bg-slate-900`、`bg-white`等）。

**修复**：如有硬编码，替换为 `style={{backgroundColor: theme.colors.xxx}}` 或 tailwind的 `bg-primary` 等token class。

---

#### T12: 系统配置编辑验证

**验证**：SystemConfigManager中点击编辑→修改值→实时校验是否生效。

**修复**：如编辑表单无校验，补充数字范围校验（如锁定时长>0）、格式校验（邮箱格式）。

---

## §执行顺序

```
Day 1: T1(UserManagement拆分) → T2(租户入口移除)
Day 2: T3(用户详情抽屉) → T4(批量操作) → T5(密码强度)
Day 3: T6(i18n) → T7(骨架屏) → T8(Toast) → T9(确认弹窗) → T10~T12(验证)
```

P0/P1 严格按序执行。P2 可自由调整顺序。

---

## §验收

```bash
# 1. 文件存在
ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/user-management/UserList.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/user-management/UserEditModal.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/user-management/UserRoleBinding.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/user-management/UserFilter.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/user-management/UserDetailDrawer.tsx

# 2. 租户入口已移除
grep -c "租户管理" /home/guorongxiao/ECOS/ecos_frontend/src/App.tsx
# 期望: 0

# 3. 通用组件存在
ls /home/guorongxiao/ECOS/ecos_frontend/src/components/common/ConfirmDialog.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/components/common/SkeletonTable.tsx
ls /home/guorongxiao/ECOS/ecos_frontend/src/components/common/Toast.tsx

# 4. 前端编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | tail -1

# 5. 浏览器验证
# - http://localhost:3000/#/security-center
#   → 身份与访问Tab：表格有checkbox列，点击行打开用户详情抽屉
#   → 新建用户：密码输入框下方有强度条
#   → 勾选用户：底部悬浮批量操作栏
#   → 删除用户：弹出确认对话框
# - 侧边栏无"租户管理"入口
# - 切换英文：侧边栏/按钮文本正确翻译
# - 1366x768分辨率：布局正常
```
