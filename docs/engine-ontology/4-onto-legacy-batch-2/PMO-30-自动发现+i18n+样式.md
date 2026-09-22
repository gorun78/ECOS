# PMO-30: 自动发现前端UI + i18n P0档 + 样式统一

> **架构铁律**: 必须遵循 `/home/guorongxiao/ECOS/docs/ARCHITECTURE-RULES.md` + `.aider.md`
> **差距分析**: `/home/guorongxiao/ECOS/docs/4-onto/01-差距分析.md` §7.1, §7.6
> 来源: 肖国荣 | 日期: 2026-08-08 | 优先级: 🔴 P0
> **范围**: 前端 `src/pages/ontology/` + `src/locales/` | **工期**: 3.5天 | **协同**: ECOS-FE
> **工具**: 全部前端任务使用 **aider**（已配置 `.aider.md` + `.aiderignore` + `ARCHITECTURE-RULES.md`）

---

## §背景

后端 `AutoDiscoverController` + `AutoDiscoverService`(214行) 已就绪——能从DB schema自动推荐本体对象。但前端完全没有触发入口。这是当前最大短板：Phase 3 已接入真实财务数据，却不能一键生成本体。

同时三大核心页面（DomainDesignerView/DomainListView/OntologyWorkbenchLayout）合计~1600硬编码中文字符串未国际化，8处硬编码颜色未对齐Theme tokens。

---

## §aider 使用方式

```bash
cd /home/guorongxiao/ECOS
# aider 已配置 read: .aider.md + docs/ARCHITECTURE-RULES.md
# aider 已配置 subtree-only: ecos_frontend
# aider 已配置 .aiderignore 排除噪音

# 每个Task一条aider指令：
aider --message "任务描述（精确到文件路径+验收标准）"
```

aider会自动加载工程全貌+铁律+Theme tokens定义，不需要重复告知技术规范。

---

## §禁止清单

1. ❌ 不硬编码Tailwind颜色 — 必须用 `useTheme().styles`（铁律4.1）
2. ❌ 不硬编码中文字符串 — T3做完后必须用 `t("ontology.xxx")`（铁律4.3）
3. ❌ 不自定义SVG图标 — 只用 `lucide-react`（铁律4.2）
4. ❌ 文件不超800行，新增文件≤400行（铁律4.6）
5. ❌ 不改后端API路径
6. ❌ AutoDiscoverPanel 不做i18n——本指令先做完T3全局i18n再建T1（顺序：T3→T1→T2）

---

## §Task

### T1: 自动发现前端UI（1.5天）— 在T3(i18n)之后执行

**新建文件**:
- `src/pages/ontology/AutoDiscoverPanel.tsx`（≤400行）
- `src/pages/ontology/AutoDiscoverPreview.tsx`（≤250行，实体预览子组件）

**修改文件**:
- `src/pages/DomainDesignerView.tsx` — 加"自动发现"按钮入口（拆分后见PMO-31）
- `src/services/ontologyApi.ts` — 加 `autoDiscover()` / `previewEntities()` 接口

**UI三步流程**:

```
Step 1 — 选择数据源:
  ┌─────────────────────────────────────┐
  │ 🔍 自动发现本体对象                   │
  │                                     │
  │ 数据源: [科创财务PG ▼]              │
  │ Schema:  [public ▼]                 │
  │                                     │
  │ [下一步: 预览候选实体]               │
  └─────────────────────────────────────┘

Step 2 — 预览候选实体:
  ┌─────────────────────────────────────┐
  │ 发现 5 个候选实体                     │
  │                                     │
  │ ☑ fin_revenue → 营收表 (8字段) 95% │
  │ ☑ fin_cost    → 成本表 (6字段) 92% │
  │ ☑ fin_orders  → 订单表 (10字段) 88%│
  │ ☐ sys_log     → 系统日志 (3字段) 45%│
  │ ☐ tmp_export  → 临时表 (2字段) 30% │
  │                                     │
  │ [上一步]          [确认生成(3个)]    │
  └─────────────────────────────────────┘

Step 3 — 生成结果:
  ┌─────────────────────────────────────┐
  │ ✅ 已创建 3 个本体对象                │
  │                                     │
  │ • fin_revenue (8属性, 1映射)         │
  │ • fin_cost (6属性, 1映射)            │
  │ • fin_orders (10属性, 1映射)         │
  │                                     │
  │ [进入设计器查看]                     │
  └─────────────────────────────────────┘
```

**后端API对接**:
- `POST /api/v1/ecos/domains/{domainCode}/auto-discover` — 触发自动发现（已有）
  - 参数: `{datasourceId, resourceNames: ["fin_revenue","fin_cost"]}`
- `GET /api/v1/ecos/entity-mappings?domainCode=finance` — 查询已创建映射（已有）

**验收**(T1):
- DomainDesignerView→"自动发现"按钮→三步向导完整走通
- 选择科创财务PG→预览5个表→勾选3个→确认→新实体出现在设计器
- 新实体属性类型映射正确（INT→NUMBER, VARCHAR→STRING, NUMERIC→DECIMAL）

---

### T2: 样式统一 — 硬编码颜色→Theme tokens（1天）

**范围**: 本体工作台相关组件硬编码 `bg-white`/`bg-[#...]`/`border-[#...]` → `useTheme().styles`

**涉及文件**:

| 文件 | 硬编码处 | 替换为 |
|------|:--:|------|
| `src/pages/DomainDesignerView.tsx` | ~15 | `styles.cardBg`/`styles.cardBorder`/`styles.appBg` |
| `src/pages/DomainListView.tsx` | ~8 | 同上 |
| `src/pages/OntologyDesigner.tsx` | ~5 | 同上 |
| `src/components/ontology-workbench/OntologyWorkbenchSidebar.tsx` | 5 | 同上 |
| `src/components/aiworkbench/chatbot/OntologyContextPanel.tsx` | 1 | 同上 |
| `src/components/aiworkbench/logic/OntologyNode.tsx` | 2 | 同上 |

**验收**(T2):
```bash
cd /home/guorongxiao/ECOS/ecos_frontend
# 硬编码颜色清零（本体相关）
grep -rn 'bg-white\|bg-\[#\|border-\[#' src/pages/DomainDesignerView.tsx \
  src/pages/DomainListView.tsx src/pages/OntologyDesigner.tsx \
  src/components/ontology-workbench/ src/components/aiworkbench/chatbot/OntologyContextPanel.tsx \
  src/components/aiworkbench/logic/OntologyNode.tsx \
  --include="*.tsx" | grep -v node_modules | wc -l
# 期望: 0

# 4主题切换验证（手动）: slate-light/deep-space/cyber-terminal/royal-purple 全部正常
```

---

### T3: i18n P0档 — 三大核心页面国际化（1天）

**目标**: DomainDesignerView(661) + DomainListView(450) + OntologyWorkbenchLayout(504) 硬编码中文→`ontology.*` namespace

**i18n文件**:

| 文件 | 操作 | 新增keys |
|------|------|:--:|
| `src/locales/zh-CN.json` | 追加 | ~120 |
| `src/locales/en.json` | 追加 | ~120 |

**命名规范**:

| 页面 | namespace前缀 | 示例 |
|------|------|------|
| DomainDesignerView | `ontology.designer.*` | `ontology.designer.addEntity`, `ontology.designer.searchPlaceholder` |
| DomainListView | `ontology.list.*` | `ontology.list.filterAll`, `ontology.list.sortByName` |
| OntologyWorkbenchLayout | `ontology.workbench.*` | `ontology.workbench.objectTypes`, `ontology.workbench.exportJson` |

**迁移方法**:
1. 扫描每个文件硬编码中文→提取为 `ontology.xxx.yyy` key
2. `zh-CN.json` 写中文原文，`en.json` 写英文翻译
3. 组件中 `"编辑实体"` → `{t("ontology.designer.editEntity")}`
4. 表单label/按钮文本/placeholder/提示信息——全部替换

**验收**(T3):
```bash
cd /home/guorongxiao/ECOS/ecos_frontend && python3 -c "
import re
for f in ['DomainDesignerView','DomainListView','OntologyWorkbenchLayout']:
    fp = f'src/pages/{f}.tsx'
    if os.path.exists(fp):
        with open(fp) as fh:
            cnt = sum(1 for l in fh if re.search(r'[\u4e00-\u9fa5]', l) and not l.strip().startswith('//'))
        print(f'{f}: {cnt}行中文残留')
# 期望: 全部 0
"
```

---

## §验证门禁

```bash
# V1: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"

# V2: Vite构建
npm run build 2>&1 | tail -3

# V3: 硬编码中文清零（T3验收脚本）
# V4: 硬编码颜色清零（T2验收脚本）
# V5: 自动发现三步流程走通（浏览器手动验证）
```

---

## §工时

| Task | 工期 | 依赖 | 工具 |
|------|:--:|------|------|
| T3 i18n P0 | 1天 | — | aider |
| T1 自动发现UI | 1.5天 | T3(i18n完成后) | aider |
| T2 样式统一 | 1天 | — | aider |
| **合计** | **3.5天** | T1依赖T3 | |
