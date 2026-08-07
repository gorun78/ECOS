# PMO-23: 数据工作台大文件拆分

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🔴 P0
> **范围**: 前端 `src/pages/data-workbench/` + 相关顶级页面
> **工期**: 4天 | **协同**: ECOS-FE

---

## §背景

数据工作台前端有6个文件>800行，其中DataWorkbenchLayout高达3791行。Phase 3要求所有文件≤800行，主Layout<300行。

必须同时完成：拆分 + 12 Tab独立文件化 + DataSourceManager改为向导式 + 删除LineageMapView。

---

## §禁止清单

1. ❌ 拆分后JSX结构必须一致，确保UI零退化
2. ❌ 不新增npm依赖
3. ❌ 不改后端API路径
4. ❌ 拆分时不改任何功能逻辑——纯结构重构
5. ❌ **不在此指令中做i18n**——i18n见PMO-25（避免同一文件被两条指令同时改）
6. ❌ 不硬编码Tailwind颜色（铁律4.1）
7. ❌ 文件不超800行（铁律4.6）

---

## §Task

### T1: DataWorkbenchLayout拆分（2天）

**文件**: `src/pages/DataWorkbenchLayout.tsx` (3791行 → <300行)

**拆分方案**：12个Tab各独立文件 + Shell仅保留Tab切换+状态管理

| 子文件 | 路径 | 来源 | 目标行数 |
|--------|------|------|:--:|
| `DataWorkbenchLayout.tsx` | `src/pages/DataWorkbenchLayout.tsx` | 重写为Shell | <300 |
| `ConnectionsTab.tsx` | `src/pages/data-workbench/tabs/ConnectionsTab.tsx` | 已有，需补全 | ≤400 |
| `SyncsTab.tsx` | `src/pages/data-workbench/tabs/SyncsTab.tsx` | 已有，需补全 | ≤400 |
| `PipelinesTab.tsx` | `src/pages/data-workbench/tabs/PipelinesTab.tsx` | 已有，需补全 | ≤400 |
| `HealthTab.tsx` | `src/pages/data-workbench/tabs/HealthTab.tsx` | 已有，需补全 | ≤400 |
| `DataLineageTab.tsx` | `src/pages/data-workbench/tabs/DataLineageTab.tsx` | 已有，需补全 | ≤400 |
| `PipelineBuilderTab.tsx` | `src/pages/data-workbench/tabs/PipelineBuilderTab.tsx` | 已有 | ≤400 |
| `CodeReposTab.tsx` | `src/pages/data-workbench/tabs/CodeReposTab.tsx` | 已有 | ≤400 |
| `CodeWorkbooksTab.tsx` | `src/pages/data-workbench/tabs/CodeWorkbooksTab.tsx` | 已有 | ≤400 |
| `ContourTab.tsx` | `src/pages/data-workbench/tabs/ContourTab.tsx` | 已有 | ≤400 |
| `SqlQueryTab.tsx` | `src/pages/data-workbench/tabs/SqlQueryTab.tsx` | 新建 | ≤400 |
| `EngineConfigTab.tsx` | `src/pages/data-workbench/tabs/EngineConfigTab.tsx` | 新建 | ≤400 |

**实现要求**：
1. Shell保留：Tab切换按钮组 + 状态管理(`activeTab`) + Copilot面板开关
2. 每个Tab是独立React组件，接收`{ showToast, ...共享props }`
3. Tab切换不重新挂载其他Tab（用CSS `display:none` 或 条件渲染保持状态）
4. 接口类型(`DataWorkbenchLayoutProps`)不变，确保调用方(Sidebar路由)无需改动

---

### T2: DataSourceManager改为向导式（1.5天）

**文件**: `src/pages/DataSourceManager.tsx` (906行) → 拆为3文件

| 新文件 | 路径 | 目标行数 |
|--------|------|:--:|
| `DataSourceList.tsx` | `src/pages/datasource/DataSourceList.tsx` | ≤300 |
| `DataSourceWizard.tsx` | `src/pages/datasource/DataSourceWizard.tsx` | ≤350 |
| `ConnectionTest.tsx` | `src/pages/datasource/ConnectionTest.tsx` | ≤150 |

**向导式3步流程**：
1. **Step 1: 填连接** — 数据源名称 + DB类型(6种全保留) + JDBC URL + 用户名 + 密码
2. **Step 2: 测试连接** — 调用`testDataSourceConnection` API，显示成功/失败+错误详情
3. **Step 3: 导入表结构** — 调用`collectMetadata` API，显示发现N张表，确认导入

**保留6种DB类型**：Oracle/MySQL/PostgreSQL/SQLServer/达梦/金仓（按决策1）

**curl验收（编译）**:
```bash
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "DataSourceManager\|DataSourceList\|DataSourceWizard\|ConnectionTest"
# 期望: 0
```

---

### T3: PipelineFlowEditor拆分（1天）

**文件**: `src/pages/data-workbench/PipelineFlowEditor.tsx` (849行) → 拆为3文件

| 新文件 | 路径 | 目标行数 |
|--------|------|:--:|
| `FlowCanvas.tsx` | `src/pages/data-workbench/pipeline-editor/FlowCanvas.tsx` | ≤300 |
| `NodePalette.tsx` | `src/pages/data-workbench/pipeline-editor/NodePalette.tsx` | ≤200 |
| `PropertyPanel.tsx` | `src/pages/data-workbench/pipeline-editor/PropertyPanel.tsx` | ≤300 |

> 注：`PropertyPanel.tsx` 已存在(567行→需减到≤300)。将Editor的逻辑部分拆出，PropertyPanel只保留属性表单。

---

### T4: 删除LineageMapView，增强DataLineage（1天）

**删除**: `src/pages/data-workbench/LineageMapView.tsx` (1233行)

**保留+增强**: `src/pages/DataLineage.tsx` (223行)

**增强项**：
1. 对接后端`GET /api/v1/engine/data/lineage`（PMO-21 T1同步交付的真实数据，非mock）
2. 节点可点击→展开详情面板（字段名/类型/来源表/转换逻辑）
3. 连线标注SQL片段
4. 搜索框高亮匹配节点
5. 保留GraphCanvas渲染方式

**验收**: `grep -r "LineageMapView" src/` 返回0匹配（Import全部替换为DataLineage）

---

## §验证门禁

```bash
# V1: 行数检查
wc -l src/pages/DataWorkbenchLayout.tsx \
     src/pages/datasource/DataSourceList.tsx \
     src/pages/datasource/DataSourceWizard.tsx \
     src/pages/datasource/ConnectionTest.tsx \
     src/pages/data-workbench/pipeline-editor/FlowCanvas.tsx \
     src/pages/data-workbench/pipeline-editor/NodePalette.tsx \
     src/pages/data-workbench/pipeline-editor/PropertyPanel.tsx
# 期望: 全部≤400 (DataWorkbenchLayout<300)

# V2: LineageMapView删除确认
test ! -f src/pages/data-workbench/LineageMapView.tsx && echo "PASS: 已删除" || echo "FAIL: 仍存在"
grep -r "LineageMapView" src/ --include="*.tsx" --include="*.ts" | grep -v "node_modules" | grep -v ".git"
# 期望: 0匹配（或仅在注释中）

# V3: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"
# 期望: 0（或仅既有error，无新增）

# V4: Vite构建
cd /home/guorongxiao/ECOS/ecos_frontend && npm run build 2>&1 | tail -5
# 期望: ✓ built in
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 Layout拆分 | 2天 | — |
| T2 DataSource向导式 | 1.5天 | — |
| T3 PipelineFlowEditor拆分 | 1天 | — |
| T4 血缘视图统一 | 1天 | PMO-21 T1(后端血缘API) |
