# PMO-31: DomainDesignerView拆分 + ER图M2 + 版本时间线 + i18n P1

> **架构铁律**: 必须遵循 `/home/guorongxiao/ECOS/docs/ARCHITECTURE-RULES.md` + `.aider.md`
> **差距分析**: `/home/guorongxiao/ECOS/docs/4-onto/01-差距分析.md` §7.4, §7.5, §7.6
> 来源: 肖国荣 | 日期: 2026-08-08 | 优先级: 🟡 P1
> **范围**: 前端 `src/pages/ontology/` + `src/pages/DomainDesignerView.tsx` | **工期**: 4天 | **协同**: ECOS-FE
> **依赖**: PMO-30 (i18n P0必须在拆分前完成，确保拆分出的新组件直接用i18n key)
> **工具**: 全部前端任务使用 **aider**

---

## §背景

DomainDesignerView 825行 🔴超标，需要拆分为 Shell≤200 + 3子组件。ER图当前是SVG静态画布，需升级M2（拖拽+右键菜单+框选）。版本管理后端有diff API但前端无时间线UI。P1档i18n覆盖OntologyObjectBrowser+OntologyDesigner。

---

## §aider 使用方式

```bash
cd /home/guorongxiao/ECOS
# 每个Task一条指令
aider --message "精确定义的任务描述+文件路径+验收标准"
```

**关键提醒**: aider已读取 `.aider.md`（含铁律：文件≤800行、不硬编码颜色/中文、用Theme tokens、lucide-react图标），无需在指令中重复。

---

## §禁止清单

1. ❌ 拆分时JSX结构不变——UI零退化
2. ❌ 不新增npm依赖（拖拽用原生事件，不引入react-dnd）
3. ❌ 新组件不超400行，Shell≤200行
4. ❌ 不改后端API路径
5. ❌ T1不引入新依赖——SVG拖拽用原生 `onMouseDown/onMouseMove/onMouseUp`
6. ❌ T4时间线不做可视化时间轴——先做列表视图

---

## §Task

### T1: DomainDesignerView 拆分（1天）

**文件**: `src/pages/DomainDesignerView.tsx` (825行) → 拆为 Shell + 3子组件

| 新文件 | 路径 | 目标行数 | 职责 |
|--------|------|:--:|------|
| `DomainDesignerView.tsx` | `src/pages/DomainDesignerView.tsx` | ≤200 | Shell：三栏布局+状态管理 |
| `EntityTreePanel.tsx` | `src/pages/ontology/EntityTreePanel.tsx` | ≤300 | 左侧：实体树+搜索+过滤 |
| `DomainCanvas.tsx` | `src/pages/ontology/DomainCanvas.tsx` | ≤350 | 中间：SVG画布+节点渲染 |
| `PropertyEditor.tsx` | `src/pages/ontology/PropertyEditor.tsx` | ≤300 | 右侧：属性表单+Function编辑 |

**拆分原则**:
- Shell只保留：`activeTab`/`selectedEntity`/`selectedProperty` 状态 + 三栏 `flex` 布局
- 子组件通过props通信：`{ entities, selectedEntity, onSelect, ... }`
- 不在此指令中做功能增强——纯结构重构

**验收**(T1):
```bash
wc -l src/pages/DomainDesignerView.tsx \
     src/pages/ontology/EntityTreePanel.tsx \
     src/pages/ontology/DomainCanvas.tsx \
     src/pages/ontology/PropertyEditor.tsx
# 期望: ≤200 / ≤300 / ≤350 / ≤300
```

```bash
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"
# 期望: 0（无新增error）
```

---

### T2: ER 图 M2 升级（2天）

**文件**:
- `src/pages/ontology/DomainCanvas.tsx` — 重写，加拖拽+右键菜单+框选
- `src/pages/ontology/CanvasContextMenu.tsx` — **新建**（≤150行）
- `src/pages/ontology/CanvasSelectionBox.tsx` — **新建**（≤100行）

**M2功能清单**:

| 功能 | 实现方式 | 验收 |
|------|------|------|
| 节点拖拽 | SVG `onMouseDown/onMouseMove/onMouseUp` 原生事件 | 拖节点→松开→位置更新；刷新页面→位置保持（localStorage） |
| 右键菜单 | `onContextMenu` 事件 + 绝对定位浮层 | 右键实体→弹出菜单（编辑/删除/添加关系/添加属性） |
| 框选 | Shift+拖拽绘制矩形→选中框内节点 | Shift+拖拽→矩形选区→框内节点高亮 |
| 连线高亮 | hover关系线→加粗+显示标签tooltip | hover线→变粗→标签浮现 |
| 点击选中 | 点击实体/关系→右侧属性面板联动 | 点实体→PropertyEditor显示该实体属性 |

**右键菜单项**:

| 菜单项 | 图标 | 操作 |
|--------|------|------|
| 编辑实体 | `Pencil` | 打开实体编辑模态框 |
| 删除实体 | `Trash2` | 确认→调DELETE API |
| 添加关系 | `Link` | 进入连线模式（点源→点目标→选关系类型） |
| 添加属性 | `Plus` | 右侧PropertyEditor切换到新增模式 |

**验收**(T2):
- 拖节点→位置保持→刷新后仍在
- 右键→"添加属性"→PropertyEditor出现空白表单
- Shift+拖选框选→多个节点高亮
- hover连线→标签显示关系名

---

### T3: 版本时间线前端（1天）

**新建文件**: `src/pages/ontology/VersionTimeline.tsx`（≤300行）

**修改文件**:
- `src/pages/DomainDesignerView.tsx` — 加"版本历史"按钮
- `src/services/ontologyApi.ts` — 加版本列表/diff接口

**列表视图设计**:
```
┌─────────────────────────────────────────┐
│ 📜 版本历史                    [关闭 ✕]  │
├─────────────────────────────────────────┤
│                                         │
│ ● v3.2  2026-08-07  肖国荣  当前版本    │
│   ├ 新增: fin_invoice 实体               │
│   └ 修改: fin_revenue 增加字段 tax_rate  │
│                                         │
│ ● v3.1  2026-07-28  张三                │
│   └ 新增: fin_cost 实体                  │
│                                         │
│ ● v3.0  2026-07-15  李四                │
│   ├ 修改: fin_revenue 字段类型调整       │
│   └ 删除: tmp_calc 实体                  │
│                                         │
│ ... (更多历史版本)                        │
└─────────────────────────────────────────┘
```

**版本diff**: 点击版本卡片→展开diff面板
```
┌─ v3.2 vs v3.1 ─────────────────────────┐
│ + fin_invoice (新增实体)                 │
│   + invoice_no: STRING                  │
│   + amount: DECIMAL                     │
│   + invoice_date: DATE                  │
│ M fin_revenue.tax_rate (NUMBER, 修改)   │
└─────────────────────────────────────────┘
```

**后端API对接**:
- `GET /api/v1/ontology/versions?domainCode=finance` — 版本列表（已有）
- `GET /api/v1/ontology/versions/diff?v1=3.1&v2=3.2` — 版本diff（已有）
- `GET /api/v1/ontology/versions/simple?domainCode=finance` — 简化版本列表（已有）

**验收**(T3):
- DomainDesignerView→"版本历史"按钮→列表显示版本卡片
- 点击v3.2→展开diff面板→显示 vs v3.1 的属性增减
- 版本卡片显示：版本号/日期/作者/变更摘要

---

### T4: i18n P1档（0.5天）

**范围**: OntologyObjectBrowser(456) + OntologyDesigner(~300)

**i18n文件**: `src/locales/zh-CN.json` + `src/locales/en.json` 追加 ~60 keys

**命名规范**:

| 页面 | namespace前缀 | 示例 |
|------|------|------|
| OntologyObjectBrowser | `ontology.browser.*` | `ontology.browser.entityList`, `ontology.browser.propertyCount` |
| OntologyDesigner | `ontology.designer.*` (复用P0档namespace) | `ontology.designer.functionType`, `ontology.designer.expressionEditor` |

**验收**(T4):
```bash
cd /home/guorongxiao/ECOS/ecos_frontend && python3 -c "
import re, os
for f in ['OntologyObjectBrowser','OntologyDesigner']:
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
# V1: 行数检查 (T1)
wc -l src/pages/DomainDesignerView.tsx \
     src/pages/ontology/EntityTreePanel.tsx \
     src/pages/ontology/DomainCanvas.tsx \
     src/pages/ontology/PropertyEditor.tsx \
     src/pages/ontology/CanvasContextMenu.tsx \
     src/pages/ontology/CanvasSelectionBox.tsx \
     src/pages/ontology/VersionTimeline.tsx

# V2: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"

# V3: Vite构建
npm run build 2>&1 | tail -3

# V4: P0+P1 i18n 中文残留总量
python3 -c "..." # 覆盖全部6个页面，期望 total=0

# V5: 手动验证
# - 拆分后UI与拆分前完全一致
# - 拖拽节点→刷新→位置保持
# - 右键菜单正常弹出
# - 版本时间线显示版本历史
```

---

## §工时

| Task | 工期 | 依赖 | 工具 |
|------|:--:|------|------|
| T1 拆分 | 1天 | PMO-30 T3(i18n P0完成) | aider |
| T2 ER M2 | 2天 | T1 | aider |
| T3 版本时间线 | 1天 | PMO-28(提案+版本) | aider |
| T4 i18n P1 | 0.5天 | PMO-30 T3 | aider |
| **合计** | **4天** | | |
