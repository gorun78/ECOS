# PMO-25: 数据工作台功能增强+i18n

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🟡 P1
> **范围**: 前端 `src/pages/data-workbench/` + `src/pages/Data*.tsx` + 国际化
> **工期**: 5天 | **协同**: ECOS-FE
> **依赖**: PMO-23(拆分完成后才能做功能增强)

---

## §背景

数据工作台有大文件拆分(PMO-23)后，需要：
1. 数据目录从卡片式改为树形结构+右键菜单
2. DQ规则模板库预置
3. 虚拟滚动
4. **~1000行硬编码中文→i18n**
5. 样式统一对齐Theme tokens

---

## §禁止清单

1. ❌ 不硬编码Tailwind颜色 — 必须用`useTheme().styles`（铁律4.1）
2. ❌ 不硬编码中文字符串 — 必须用`t("databench.xxx")`（铁律4.3）
3. ❌ 不自定义SVG图标 — 只用`lucide-react`（铁律4.2）
4. ❌ 文件不超800行（铁律4.6）
5. ❌ 不改后端API路径

---

## §Task

### T1: 数据目录改为树形结构+右键菜单（2天）

**文件**:
- `src/pages/DataCatalog.tsx` (335行 → 重写)
- 新建 `src/pages/data-workbench/CatalogTree.tsx` (≤300行)
- 新建 `src/pages/data-workbench/CatalogContextMenu.tsx` (≤150行)

**树形结构**：
```
📁 数据源: 科创财务PG
  📁 schema: public
    📁 表: fin_revenue (营收表)
      📄 字段: id, amount, period, customer_id...
    📁 表: fin_cost (成本表)
    📁 表: fin_orders (订单表)
  📁 schema: analytics
    ...
📁 数据源: 江粮MySQL
  ...
```

**右键菜单**（3个操作）：
| 菜单项 | 图标 | 操作 |
|--------|------|------|
| 预览数据 | `Eye` | 弹窗显示前100行，分页 |
| 查看血缘 | `GitBranch` | 跳转到DataLineage并高亮该表 |
| 配置DQ规则 | `Shield` | 跳转到DataQualityDashboard并预填表名 |

**实现要求**：
1. 数据从`fetchAllResources` API获取，构建树形结构(dataSource→schema→table→field)
2. 支持展开/折叠，搜索过滤高亮
3. 右键菜单用`onContextMenu`事件+绝对定位浮层
4. 树节点图标：数据源=`Database`、Schema=`Folder`、表=`Table`、字段=`Columns`

---

### T2: DQ规则模板库预置（1.5天）

**文件**: `src/pages/DataQualityDashboard.tsx` (修改，≤500行) + 新建 `src/pages/data-workbench/RuleTemplateLibrary.tsx` (≤200行)

**5类预置模板**：

| 模板名 | 类型 | 默认参数 | 说明 |
|--------|------|------|------|
| 空值率检查 | COMPLETENESS | threshold=0.05 | 字段空值占比>5%告警 |
| 重复率检查 | UNIQUENESS | threshold=0.01 | 重复行占比>1%告警 |
| 值域范围 | VALIDITY | min/max | 数值字段超出[min,max]告警 |
| 格式校验 | VALIDITY | regex | 匹配正则(邮箱/手机/日期) |
| 自定义SQL | ACCURACY | sql | 自定义检查SQL，返回count |

**一键应用**：
1. 从模板库选择模板→选择目标表→选择字段→自动生成DQ规则
2. 生成后可修改参数后再保存

**实现要求**：
1. 模板库以JSON常量内联（5条），不额外建配置文件
2. 每个模板有：名称(中英文)、类型、图标(lucide-react)、默认参数、适用场景描述
3. "一键应用"弹窗：选择表(dropdown)→选择字段(multi-select)→自动填充参数→确认创建

---

### T3: 虚拟滚动（1天）

**文件**: `src/pages/DatasetExplorer.tsx` (748行) + `src/pages/DataCatalog.tsx`

**实现要求**：
1. 引入`react-window` FixedSizeList（如果未安装则`@tanstack/react-virtual`）
2. 列表>100行数据时自动启用虚拟滚动
3. 后端分页：LIMIT 10000 + 超时30s（与PMO-21 T4配套）

**验收**: 列表>1000条数据时滚动不卡顿（DevTools Performance无长任务>50ms）

---

### T4: i18n国际化 — 数据工作台（2天）

**新增namespace**: `databench`（约120 keys）

**locale文件**: `src/i18n/locales/databench/zh-CN.json` + `en.json`

**涉及文件**（~20个）: 全部数据工作台相关页面和组件

**中文行数统计**（从差距分析）：
| 文件 | 中文行数 |
|------|:--:|
| DataWorkbenchLayout.tsx | 418 |
| pbFunctions.ts | 132 |
| DataEngineConfigPanel.tsx | 90 |
| DatasetExplorer.tsx | 82 |
| CopilotPanel.tsx | 53 |
| DataQualityDashboard.tsx | 46 |
| PipelineFlowEditor.tsx | 44 |
| PipelineExecutionMonitor.tsx | 27 |
| DataMaskingDemo.tsx | 27 |
| DataCatalog.tsx | 24 |
| ... 其余~10个文件 | ~100 |
| **合计** | **~1000** |

**迁移方法**：
1. 扫描每个文件的硬编码中文→提取到`databench.xxx` key
2. Key命名：`databench.<页面>.<元素>` (如`databench.catalog.search.placeholder`)
3. 所有`t("中文...")` 或 `locale==="zh"?"中文":"English"` 替换为 `t("databench.xxx")`
4. `en.json` 同步翻译

**验收**:
```bash
cd /home/guorongxiao/ECOS/ecos_frontend && python3 -c "
import re, os
total = 0
for root, dirs, files in os.walk('src/pages/data-workbench'):
    for f in files:
        if f.endswith('.tsx'):
            with open(os.path.join(root,f)) as fh:
                cnt = sum(1 for l in fh if re.search(r'[\u4e00-\u9fa5]', l))
            if cnt > 0: print(f'{f}: {cnt}'); total += cnt
# 也检查顶级Data*.tsx
for f in ['DataCatalog.tsx','DataLake.tsx','DataLineage.tsx','DataQualityDashboard.tsx',
          'DataSourceManager.tsx','DataWorkbenchLayout.tsx','DatasetExplorer.tsx',
          'PipelineBuilder.tsx','SqlQueryConsole.tsx','DataMaskingDemo.tsx']:
    fp = f'src/pages/{f}'
    if os.path.exists(fp):
        with open(fp) as fh:
            cnt = sum(1 for l in fh if re.search(r'[\u4e00-\u9fa5]', l))
        if cnt > 0: print(f'{f}: {cnt}'); total += cnt
print(f'TOTAL: {total}')
# 期望: TOTAL: 0 (注释除外)
"
```

---

### T5: 样式统一（1天）

**涉及文件**: 数据工作台全部~20个页面/组件

**实现要求**：
1. 扫描所有数据工作台文件，替换硬编码Tailwind颜色：
   - `bg-white` → `styles.cardBg`
   - `border-gray-200` → `styles.cardBorder`
   - `text-gray-500` → `styles.cardTextMuted`
   - `bg-gray-50` → `styles.appBg`
2. 保留**非结构性的语义色**：状态标签的绿色/红色/黄色
3. 4主题切换后无白块/黑块

**验收**: 手动切4个主题，数据工作台所有Tab显示正常

---

## §验证门禁

```bash
# V1: i18n文件存在
test -f src/i18n/locales/databench/zh-CN.json && echo "PASS: zh-CN" || echo "FAIL: zh-CN"
test -f src/i18n/locales/databench/en.json && echo "PASS: en" || echo "FAIL: en"

# V2: 硬编码中文清零
python3 -c "..." # 同T4验收脚本

# V3: 硬编码颜色检查 (白名单语义色除外)
grep -rn "bg-white\|\"bg-gray-\|border-gray-" src/pages/data-workbench/ src/pages/Data*.tsx --include="*.tsx" | grep -v "node_modules" | grep -v "//" | wc -l
# 期望: 0 (如有false positive人工判断)

# V4: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"
# 期望: 0新增

# V5: Vite构建
cd /home/guorongxiao/ECOS/ecos_frontend && npm run build 2>&1 | tail -3
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 树形目录+右键菜单 | 2天 | PMO-23 T1(拆分完) |
| T2 DQ模板库 | 1.5天 | — |
| T3 虚拟滚动 | 1天 | — |
| T4 i18n 1000行 | 2天 | PMO-23 T1-T4(文件名稳定后) |
| T5 样式统一 | 1天 | T1-T4 |
