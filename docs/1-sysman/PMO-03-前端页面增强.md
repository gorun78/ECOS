# PMO指令：Phase1-sysman-03 — 前端页面增强

> **完善计划**: T3 | **工期**: 2天 | **范围**: 前端3个页面 | **依赖**: PMO-01

---

## §禁止清单

1. ❌ 不改后端Java
2. ❌ 不改API端点路径
3. ❌ 不引入新的npm包
4. ❌ 中文文案统一走`t("key")`（国际化key先在zh-CN.json中定义），不硬编码
5. ❌ 不改SecurityCenter.tsx（T4才动）
6. ❌ 不删TenantManager.tsx

---

## §Task

### T3a: UserManagement.tsx 拆分 + 增强

**文件**: `ecos_frontend/src/pages/UserManagement.tsx`（1460行 → 拆为4个文件）

**拆分**: 

| 文件 | 提取内容 | 行数目标 |
|------|---------|:--:|
| `UserManagement.tsx` | 保留：页面容器+筛选栏+列表+空状态 | ~400 |
| `components/user/UserEditModal.tsx` | 新建：用户编辑弹窗（基本信息/角色绑定/安全Profile三Tab） | ~300 |
| `components/user/UserRoleBinding.tsx` | 新建：角色绑定组件（多选列表） | ~150 |
| `components/user/UserFilter.tsx` | 新建：筛选栏组件（角色下拉+状态下拉+搜索框） | ~100 |

**各组件职责**:

| # | 功能 | 实现要求 |
|---|------|---------|
| 1 | 角色筛选 | 顶部筛选栏加角色多选下拉。调`GET /api/v1/roles`获取角色列表 |
| 2 | 状态筛选 | 增加状态下拉：全部/正常/锁定 |
| 3 | CSV导入 | "导入"按钮→文件选择→解析CSV（用户名,显示名,邮箱,角色）→预览表格→确认导入→`POST /api/v1/users/batch`→显示结果（成功N/失败M+原因列表） |
| 4 | CSV导出 | "导出"按钮→当前筛选结果导出CSV。前端生成Blob下载，不调后端 |
| 5 | 用户详情抽屉 | 点击用户行→右侧滑出400px宽抽屉，三Tab：基本信息/角色绑定/最近登录记录。底部操作区：强制下线+重置密码 |
| 6 | 强制下线 | 详情抽屉→"强制下线"按钮→确认弹窗→`POST /api/v1/users/{id}/force-logout`→toast提示成功 |
| 7 | 密码重置 | 详情抽屉→"重置密码"→`POST /api/v1/users/{id}/reset-password`→展示临时密码（复制按钮）+提示"首次登录需修改" |
| 8 | 空状态 | 用户列表为空时显示引导卡片：图标+文案"暂无用户，点击创建或导入CSV"+创建按钮+导入按钮 |
| 9 | 操作确认 | 删除/下线/重置操作前弹出确认对话框 |
| 10 | 加载状态 | 列表首次加载和筛选切换时显示骨架屏（4行灰色占位卡片），操作提交时按钮显示loading状态 |

### T3b: SystemConfigManager.tsx 增强

**文件**: `ecos_frontend/src/pages/SystemConfigManager.tsx`

| # | 功能 | 实现要求 |
|---|------|---------|
| 1 | 分组展示 | 两组：全局通用配置 + 安全配置。组间灰色分隔线+粗体标题 |
| 2 | 安全配置靠底 | 安全组在页面下方，背景`bg-slate-50 dark:bg-slate-900`微区分 |
| 3 | 配置行展示 | 每行：key | 中文描述 | 当前值（可编辑） | 默认值（灰色） | 修改时间 | 恢复默认按钮 |
| 4 | 类型校验 | INTEGER→只允许数字输入框，BOOLEAN→下拉true/false，STRING→文本框 |
| 5 | 恢复默认 | "恢复默认"按钮→确认→调`PUT /api/v1/sysconfig/{key}/reset` |
| 6 | 保存反馈 | 修改值→保存→toast："配置 {key} 已更新" |

**分组数据**（写死在代码或从后端config_type字段区分）：

```
━━━ 全局通用配置 ━━━
 session_timeout_minutes      30     INTEGER
 audit_retention_days        180     INTEGER

━━━ 安全配置 ━━━
 password_min_length          8     INTEGER
 password_require_upper     true    BOOLEAN
 password_require_digit     true    BOOLEAN
 password_require_special   false   BOOLEAN
 password_expire_days        90     INTEGER
 password_history_count       3     INTEGER
 max_login_attempts           5     INTEGER
 lockout_duration_minutes    15     INTEGER
 max_concurrent_sessions      3     INTEGER
```

### T3c: SecurityAudit.tsx 增强

**文件**: `ecos_frontend/src/pages/SecurityAudit.tsx`

| # | 功能 | 实现要求 |
|---|------|---------|
| 1 | 筛选栏 | 用户下拉（调`GET /api/v1/users`）、操作类型下拉（登录/查询/修改/删除/导出）、时间范围（日期选择器）、IP输入框 |
| 2 | 统计面板 | 顶部4个数字卡片：今日操作数/失败数/活跃用户数/异常IP数。数据从`GET /api/v1/audit/stats`获取 |
| 3 | 时间线视图 | 左侧竖线+时间节点+右侧事件卡片。按今天/昨天/更早自动分组 |
| 4 | 分页 | 底部分页器+每页条数选择(20/50/100) |
| 5 | 导出CSV | 筛选结果导出 |
| 6 | 详情展开 | 点击事件卡片→展开：请求参数JSON、响应摘要、IP、UserAgent、耗时ms |

---

## §验收

```bash
# V1: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep "error TS" | wc -l
# 期望: 0

# V2: 启动后手动验证（截屏）
# - 用户管理: CSV导入3条→预览确认→创建成功→筛选角色=analyst只显示1条→导出CSV
# - 用户详情: 点击admin→抽屉展开→基本信息+角色+登录记录→下线按钮可点
# - 系统配置: 两组分隔显示→安全配置在下方灰色背景→改password_min_length=10→保存→toast
# - 安全审计: 筛选栏可见→统计卡片有数字→时间线分组显示

# V3: 无中文硬编码
grep -r "'[^']*[\u4e00-\u9fa5][^']*'" /home/guorongxiao/ECOS/ecos_frontend/src/pages/UserManagement.tsx 2>/dev/null | wc -l
# 期望: 0（所有中文走t()）

# V4: 后端编译不受影响
cd /home/guorongxiao/ECOS/ecos_backend && mvn compile -pl gateway -am -DskipTests -q && echo "BUILD PASS"
```
