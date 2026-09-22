# PMO指令：Phase1-sysman-04 — 安全中心统一入口 + 国际化

> **完善计划**: T4 + T5 | **工期**: 3天 | **范围**: 前端 | **依赖**: PMO-01 + PMO-03

---

## §禁止清单

1. ❌ 不改后端Java
2. ❌ 不改SecurityCenter.tsx内部逻辑，直接删除它（被新文件替代）
3. ❌ 不删除UserManagement.tsx/SystemConfigManager.tsx/SecurityAudit.tsx（T4整合引用它们，不是替代）
4. ❌ 国际化key不重复、不冲突，命名规则：`模块.页面.元素`（如`nav.securityCenter`、`user.list.empty`）
5. ❌ 事中Tab只做占位，不实现任何安全策略功能

---

## §Task

### T4a: SecurityCenter.tsx 拆分

**删除**: `ecos_frontend/src/pages/SecurityCenter.tsx`（3320行）

**新建**:

| 文件 | 行数目标 | 说明 |
|------|:--:|------|
| `pages/security-center/SecurityCenterLayout.tsx` | ~200 | 三Tab布局容器 |
| `pages/security-center/PreventTab.tsx` | ~400 | 事前Tab：内嵌用户管理组件引用 |
| `pages/security-center/DetectTab.tsx` | ~100 | 事中Tab：建设中占位 |
| `pages/security-center/AuditTab.tsx` | ~400 | 事后Tab：审计日志组件引用 |

### T4b: 三Tab实现

**SecurityCenterLayout.tsx**:
```tsx
export default function SecurityCenterLayout() {
  const [activeTab, setActiveTab] = useState<'prevent' | 'detect' | 'audit'>('prevent');
  return (
    <div>
      <TabBar active={activeTab} onChange={setActiveTab}>
        <Tab id="prevent" label={t('security.prevent')} icon={Shield} />
        <Tab id="detect"  label={t('security.detect')} icon={Scan} />
        <Tab id="audit"  label={t('security.audit')} icon={FileText} />
      </TabBar>
      {activeTab === 'prevent' && <PreventTab />}
      {activeTab === 'detect' && <DetectTab />}
      {activeTab === 'audit' && <AuditTab />}
    </div>
  );
}
```

**PreventTab.tsx** — 二级Tab: 用户管理/角色管理/权限定义:
```tsx
// 直接引用现有组件，不做功能改动
// 用户管理: <UserManagement /> (PMO-03增强后的)
// 角色管理: 复用现有RoleController对应的前端组件
// 权限定义: 复用现有PermissionController对应的前端组件
```

**DetectTab.tsx** — 占位:
```tsx
export default function DetectTab() {
  return (
    <UnderConstruction
      icon={Scan}
      title={t('security.detect.title')}
      description={t('security.detect.description')}
      items={['ABAC属性策略', '安全标记MAC', '合规分析PBAC', '行级安全RLS', '列级安全CLS', '脱敏规则']}
      eta={t('security.detect.eta')}
    />
  );
}
```

**AuditTab.tsx** — 引用PMO-03增强后的SecurityAudit组件:
```tsx
export default function AuditTab() {
  return <SecurityAudit />; // PMO-03增强后的，含筛选/时间线/统计/分页
}
```

### T4c: 侧边栏调整

**文件**: `ecos_frontend/src/App.tsx`

去掉独立入口"用户管理"、"系统配置"、"安全审计"，新增"安全中心"：

```typescript
// 改前:
const navLabels: Record<string, string> = {
  iam: "用户管理",
  "system-config": "系统配置",
  tenants: "租户管理",   // 已在PMO-01去掉
  audit: "安全审计",
  // ...
};

// 改后:
const navLabels: Record<string, string> = {
  "security-center": "安全中心",   // ← 新增，路由指向 SecurityCenterLayout
  // 去掉 iam, system-config, tenants, audit 独立入口
  // ...
};
```

路由映射:
```typescript
// 新增路由
{ path: '/security-center', component: SecurityCenterLayout }
// 保留旧路由（被整合进安全中心后仍可直链访问，但侧边栏不显示）
// /users, /system-config, /audit 保留不删
```

### T4d: LanguageContext 拆分 + 国际化抽取

**现状**: `components/LanguageContext.tsx` 2690行，翻译字符串直接写在组件内。

**拆分**:

| 文件 | 内容 | 行数目标 |
|------|------|:--:|
| `components/LanguageContext.tsx` | 只做：加载JSON→`t()`函数→语言切换→注入React Context | ~200 |
| `locales/zh-CN.json` | 所有中文翻译字符串 | — |
| `locales/en.json` | 所有英文翻译 | — |

**新建**: `ecos_frontend/src/locales/zh-CN.json`
**新建**: `ecos_frontend/src/locales/en.json`

从 `LanguageContext.tsx` 中提取所有翻译字符串到zh-CN.json。英文提供翻译。

**改造**: `LanguageContext.tsx` — 删除内嵌的翻译字符串，改为从JSON文件加载。

**覆盖范围**: sysman相关全部文案（安全中心三Tab/用户管理/系统配置/安全审计/错误提示/操作反馈）。

**命名规则**: `模块.页面.元素`
```json
{
  "nav.securityCenter": "安全中心",
  "security.prevent": "身份与访问",
  "security.detect": "安全策略",
  "security.audit": "安全审计",
  "user.list.empty": "暂无用户，点击创建或导入CSV",
  "user.import.success": "导入成功 {count} 条",
  "config.group.general": "全局通用配置",
  "config.group.security": "安全配置",
  "audit.stats.today": "今日操作数",
  "common.save": "保存",
  "common.cancel": "取消"
}
```

### T4e: 样式统一

- 安全中心所有组件颜色走 `useTheme()` 的token
- 按钮/输入框/表格/弹窗/抽屉样式对齐AI工作台既有组件
- 响应式：1920x1080和1366x768下布局正常

---

## §验收

```bash
# V1: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep "error TS" | wc -l
# 期望: 0

# V2: 安全中心三Tab可切换
# npm run dev → 浏览器访问 /security-center
# Tab1 身份与访问 → 显示用户列表（可CRUD）
# Tab2 安全策略 → 显示"建设中"+6项能力列表
# Tab3 安全审计 → 显示时间线+筛选+统计

# V3: 侧边栏变更
# 只有"安全中心"入口，无"用户管理""系统配置""安全审计"独立入口
# 旧URL /users 仍可访问但不显示在侧边栏

# V4: 国际化
# 浏览器语言=zh → 全中文
# 浏览器语言=en → 全英文

# V5: 主题切换
# dark主题下安全中心页面底色/卡片/表格正常

# V6: 旧文件已删
[ ! -f /home/guorongxiao/ECOS/ecos_frontend/src/pages/SecurityCenter.tsx ] && echo "OLD SECURITY CENTER DELETED"

# V7: 新文件存在
for f in SecurityCenterLayout PreventTab DetectTab AuditTab; do
  ls /home/guorongxiao/ECOS/ecos_frontend/src/pages/security-center/${f}.tsx
done
```

---

## §补充：国际化key覆盖清单

| 模块 | key数量 | 示例 |
|------|:--:|------|
| nav | 5 | `nav.securityCenter`, `nav.dataWorkbench`... |
| security | 10 | `security.prevent`, `security.detect.title`... |
| user | 15 | `user.list.empty`, `user.import.success`... |
| config | 10 | `config.group.general`, `config.key.password_min_length`... |
| audit | 12 | `audit.stats.today`, `audit.filter.user`... |
| common | 8 | `common.save`, `common.cancel`, `common.confirm`... |
| **合计** | **~60** | |
