# PMO指令：侧边栏重组 + 安全中心替换 + 可折叠

> **来源**: 肖总 | **日期**: 2026-07-06 | **工期**: 2天 | **角色**: FE+BE
> **铁律**: 一个Task一个commit。TS编译0新增错误。先读源码再改。

---

## 0. 目标效果

```
┌─────────────────────────┐
│ ECOS v2.0               │
├─────────────────────────┤
│ 总览                     │
│   🎯 战略目标            │
│   📊 监控中心            │
│   🛡️ 安全中心(ceos_new)  │  ← 替换为ceos_new 3290行版本，
├─────────────────────────┤     护栏融入其中
│ 资源概览                 │
│   🏪 数据市场            │
│   🧠 知识图谱            │
│   ▶️ 运营应用            │
├─────────────────────────┤
│ 系统管理                 │
│   👥 用户管理            │
│   📋 数据字典            │
│   ⚙️ 系统配置            │
│   🏢 租户管理            │
├─────────────────────────┤
│ 产品功能 (5项平铺)       │
│   📊 数据工作台          │
│   🌐 业务工作台          │
│   ⚡ 应用工作台          │
│   📚 知识库工作台        │  ← 改名
│   🤖 AI工作台            │
├─────────────────────────┤
│ [⇤ 收起]  [内核状态]    │
└─────────────────────────┘
```

---

## 1. 禁止清单

1. **禁止** 引入新npm依赖（recharts两边都有✅）
2. **禁止** 删除现有路由路径（可改组件映射，不删Route）
3. **禁止** 改动后端
4. **禁止** 产出分析文档

---

## 2. Phase 1: 安全中心替换（FE，4h）

### T1.1: 用ceos_new SecurityCenterView替换c2eos SecurityCenter（3h，FE）

**源文件**: `/home/guorongxiao/ceos_new/src/components/SecurityCenterView.tsx` (3290行)
**目标文件**: `/home/guorongxiao/c2eos/src/pages/SecurityCenter.tsx`（完全重写）

**ceos_new SecurityCenterView 包含6大模块**:
1. Security Org — 安全组织管理（隔离模式/成员/IP段/跨组织共享）
2. Project DAC — 项目级访问控制（Owner/Editor/Viewer/Discoverer角色）
3. Security Marking — 安全标记（分类分级标签）
4. Purpose PBAC — 基于目的访问控制
5. Row/Column Policy — 行列级策略
6. Audit Logs — 安全审计日志（含PieChart/LineChart/BarChart）

**适配要点**:
- 图标：ceos_new用LucideIcon wrapper → c2eos直接用lucide-react `import { ... } from "lucide-react"`
- 图表：recharts两边版本兼容（ceos_new ^3.9.1, c2eos ^3.8.1），不改API
- Toast：ceos_new接收`showToast` prop → c2eos无此机制，改为console.log或静默处理
- 样式：ceos_new用Tailwind（`bg-slate-50 text-slate-800`等）→ c2eos同Tailwind，直接可用
- 类型导出：保留`export interface SecurityOrg, ProjectDAC, ...`等，确保main.tsx import不报错
- mock数据：保留ceos_new的`mockSecurityOrgs, mockProjectDACs, ...`内联在组件中，或在同目录新建`securityMockData.ts`

**关键**: 保留GuardrailsView.tsx不动（安全中心页面内部可引用），但侧边栏不再独立显示护栏菜单。

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "SecurityCenter\|security" | grep "error TS" | wc -l
# 期望: 0
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/#/security-center
# 期望: 200 (页面可加载)
```

### T1.2: 安全中心后端Mock API（如需要，1h，BE 可选）

**判断条件**: 检查ceos_new SecurityCenterView中是否有fetch调用。

```bash
grep -n "fetch\|axios\|api\.\|/api/" /home/guorongxiao/ceos_new/src/components/SecurityCenterView.tsx | head -20
```

如果全部用mock数据（useState初始即mockData），无需后端改动。如有fetch，在`server.ts`添加对应mock端点。

---

## 3. Phase 2: 侧边栏重组（FE，2.5h）

### T2.1: 重构分组结构（2h，FE）

**改文件**: `/home/guorongxiao/c2eos/src/components/Sidebar.tsx`

#### (a) 总览组
```typescript
const overviewGroup: NavGroup = {
  group: "总览", groupZh: "总览",
  items: [
    { id: "world_model", labelZh: "战略目标", icon: Target, ... },
    { id: "monitor", labelZh: "监控中心", icon: Gauge, ... },
    { id: "security-center", labelZh: "安全中心", icon: Shield, descZh: "安全组织、访问控制、分类标记、审计日志", ... }
  ]
};
```

#### (b) 新增资源概览组
```typescript
const resourceGroup: NavGroup = {
  group: "资源概览", groupZh: "资源概览",
  items: [
    { id: "marketplace", labelZh: "数据市场", icon: Store, ... },
    { id: "knowledge_view", labelZh: "知识图谱", icon: BookOpen, descZh: "知识向量索引与RAG检索", ... },
    { id: "ops_apps", labelZh: "运营应用", icon: Play, ... }
  ]
};
```

#### (c) 系统管理组
```typescript
const systemGroup: NavGroup = {
  group: "系统管理", groupZh: "系统管理",
  items: [
    { id: "iam", labelZh: "用户管理", icon: UsersIcon, ... },
    { id: "dict", labelZh: "数据字典", icon: Table2, ... },
    { id: "system-config", labelZh: "系统配置", icon: Settings, ... },
    { id: "tenants", labelZh: "租户管理", icon: Building, ... }
  ]
};
```

#### (d) 产品功能 — 5项平铺（去掉G1-G4折叠）
```typescript
const productItems: NavItem[] = [
  { id: "data-workbench", labelZh: "数据工作台", icon: LayoutDashboard, descZh: "数据源·管道·治理·血缘·调度" },
  { id: "ontology_workbench", labelZh: "业务工作台", icon: Network, descZh: "本体建模·实体管理·关系图谱·术语标准" },
  { id: "workflow_designer", labelZh: "应用工作台", icon: GitPullRequest, descZh: "流程设计·表单搭建·运营应用" },
  { id: "knowledge_view", labelZh: "知识库工作台", icon: BookOpen, descZh: "向量库·文本切片·RAG检索" },
  { id: "agent_studio", labelZh: "AI工作台", icon: Cpu, descZh: "Agent协同·模型目录·安全审计" }
];
```

**"知识库工作台"而非"知识图谱"** — 对应产品功能组中ceos_new AIPWorkbench的KnowledgeView。

**渲染**: productItems flat渲染，保留"产品功能"标题。删掉`expandedProductGroup`状态、`productGroups`数组、`CollapsibleGroup`类型。

**去重防护**: overview/resource/system/product四组之间可能出现重复id（如knowledge_view在资源概览和产品功能都出现）。这是**有意为之**——不做去重，两边都显示。但渲染时确保key唯一（加前缀区分）。

#### (e) 删除项
- 删除"安全与审计"组及guardrails独立菜单项
- 删除G1-G4全部折叠逻辑
- 删除不再用的icon import（Layers, Globe, Briefcase, Share2等）

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep "Sidebar" | grep "error TS" | wc -l
# 期望: 0
```

### T2.2: 侧边栏可折叠（0.5h，FE）

**改文件**: `/home/guorongxiao/c2eos/src/components/Sidebar.tsx`

当前CSS：
```css
md:relative md:translate-x-0 md:opacity-100 md:pointer-events-auto
```

改为条件：
```css
md:relative md:pointer-events-auto
${collapsed ? 'md:translate-x-0 md:opacity-100' : 'md:-translate-x-full md:opacity-0 md:w-0'}
```

在Sidebar底部（"内核状态"footer上方）加折叠按钮，用ChevronLeft图标。

Topbar的汉堡按钮确认桌面端可见——如是，用户收起后可点汉堡恢复。

**验收**: 点折叠→消失→点汉堡→恢复。TS编译0错误。

---

## 4. Phase 3: 精修复 + QA（FE，3h）

### T3.1: 精修复（1h）

a. 页面加载四组正确渲染
b. 安全中心页面加载 → ceos_new版SecurityCenterView（6个Tab模块可见）
c. 资源概览三位跳转正常
d. 系统管理四位跳转正常
e. 产品功能五位平铺、跳转正常
f. 折叠/恢复交互正常
g. 当前激活高亮正确
h. 控制台无报错

### T3.2: QA 10项验证（2h）

| # | 场景 | 验证 | 期望 |
|---|------|------|------|
| 1 | 安全中心页面 | 浏览器打开 /security-center | 6个Tab模块渲染，非旧版84行wrapper |
| 2 | 安全护栏不再独立 | grep Sidebar.tsx | 无guardrails菜单项 |
| 3 | 安全中心在总览 | 目视 | "安全中心"在总览下 |
| 4 | 资源概览组 | 目视 | 数据市场/知识图谱/运营应用 |
| 5 | 产品功能flat | 目视 | 5项平铺，无G1-G4折叠 |
| 6 | 知识库工作台 | 目视 | 产品功能第4项="知识库工作台" |
| 7 | 业务工作台 | 目视 | 产品功能第2项="业务工作台" |
| 8 | 应用工作台 | 目视 | 产品功能第3项="应用工作台" |
| 9 | 折叠恢复 | 交互测试 | 点收起→消失→点汉堡→恢复 |
| 10 | TS编译 | `npx tsc --noEmit` | PMO改动0新增错误 |

---

## 5. 执行顺序

```
T1.1 (安全中心替换, 3h) ──→ T2.1 (侧边栏重组, 2h) ──→ T2.2 (可折叠, 0.5h)
                                    ↓
                              T3.1 (精修复) → T3.2 (QA)
```

T1.2（后端mock API）按需触发：先grep确认无fetch → 跳过。

---

## 6. 一句话给PMO

**三件事：①用ceos_new 3290行SecurityCenterView替换c2eos旧安全中心（护栏融入其中）②侧边栏改为四组flat结构（总览+资源概览+系统管理+5项产品功能平铺）③产品功能"知识图谱"改名为"知识库工作台"。加桌面端折叠。10项QA，一个FAIL打回。**
