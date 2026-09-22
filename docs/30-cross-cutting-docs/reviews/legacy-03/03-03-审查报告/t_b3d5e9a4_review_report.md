# REVIEW_REPORT — 数据工作台三项改动代码审查

> **task_id**: `t_b3d5e9a4` ｜ **artifact_type**: `SOURCE_PATCH` ｜ **workflow_mode**: L2
> **artifact_ref**: `release/v2.1-alpha` commit `13c10a9` / `75e2340` / `baeb7ac`（5 文件，52+/21-）
> **审查范围**: `dw/zh-CN.json`、`dw/en.json`、`DataWorkbenchLayout.tsx`、`PipelineFlowEditor.tsx`、`ecos-tests/lineage-smoke.mjs`
> **上游依据**: 用户直派指令（原文）「数据工作台修改：1、菜单名依次改为：数据源连接、数据管道、数据质量、数据血缘 2、引擎配置靠底展示，放`div`之上；3、数据管道：点击左边的数据管道，画布上未加载管道内容」
> **结论**: **PASS** — `deliverable_allowed = true`，P0=0 / P1=0 / P2=0 / P3=2（均非本批次引入）

---

## 一、审查范围与证据链

| commit | message | 文件 |
|:--|:--|:--|
| `13c10a9` | `feat(数据工作台): 侧边菜单文案精简为数据源连接/数据管道/数据质量/数据血缘` | `dw/zh-CN.json`、`dw/en.json`、`ecos-tests/lineage-smoke.mjs` |
| `75e2340` | `feat(数据工作台): 主菜单收敛为4项并将引擎配置入口移至侧边栏底部` | `DataWorkbenchLayout.tsx` |
| `baeb7ac` | `fix(数据工作台): 修复点选数据管道后画布未加载管道内容` | `PipelineFlowEditor.tsx` |

四步法（铁律 §5.4）：

| 步 | 项 | 结果 |
|:--|:--|:--|
| V1 | 文件生存 | 5 文件均修改入库，`git status --porcelain` 为空 |
| V2 | 集成点 | `TAB_CONFIG` 4 项 → `renderSideTab`；`SIDE_BOTTOM_TABS` → 底部区块（监控 `div` 之上）；`loadedDefKey` → `useEffect` 依赖 |
| V3 | 编译门 | `npm run lint`（`tsc --noEmit`）→ **exit 0** |
| V4 | 浏览器 E2E | 见 §三 |

---

## 二、逐项验收

### 2.1 需求① 菜单改名 —— **PASS**

- 中英双侧 key 齐备且同源：`dw.tab.connections` / `pipeline_builder` / `health` / `lineage` → `数据源连接`·`数据管道`·`数据质量`·`数据血缘`（en：`Data Sources`·`Data Pipelines`·`Data Quality`·`Data Lineage`）。
- 文案取值路径为 `t(tab.i18nKey)`，**无硬编码中文字面量**（铁律 §4.3）。
- 浏览器实测侧边栏标签：`['数据源连接','数据管道','数据质量','数据血缘','引擎配置']`，主菜单恰 4 项，顺序与用户指定一致。
- 受控依赖同步：`ecos-tests/lineage-smoke.mjs` 的 `TAB_LABEL` 已由 `全链路数据血缘地图` 改为 `数据血缘`（该文件上一批次刚入库，属必要同步，否则 E2E 会断）。

### 2.2 需求② 引擎配置靠底 —— **PASS**

- `TAB_CONFIG` 收敛为 4 项，`engine-config` 移入新建的 `SIDE_BOTTOM_TABS`，渲染于侧边栏底部区块、`物理数据监控仪表` 之上；侧边栏仍为 `justify-between` 两段结构，底部区块位于底部。
- 抽出 `renderSideTab` 共用按钮渲染，主菜单与底部入口**共用同一 className 与选中态判定**，避免样式漂移（符合前端规范「扩展性/一致性」）。
- 浏览器实测侧边栏文本索引：`引擎配置 = 27` < `物理数据监控仪表 = 32` → 位置正确。
- 回归：切换 `引擎配置` Tab 渲染成功，无 ErrorBoundary；其余 4 个 Tab 切换正常。

### 2.3 需求③ 画布加载 —— **PASS**

**根因**（违反铁律 §4.8-1 列表/详情配对契约的残留）：

- 列表接口 `GET /api/v1/pipeline/definitions` 只返摘要（**无 `nodes` 字段**）；编辑器选中后先以摘要渲染一次（`nodes` 空 → 副作用空跑），随后详情接口 `GET /api/v1/pipeline/definitions/{id}` 返回 `nodes.length=3`。
- 原副作用仅依赖 `[editingPipeline?.id]`：**详情晚到时 id 未变 → 副作用不再执行 → 画布恒空**。缺陷不在 `PipelineBuilderTab`（其 `canvasPipeline = detail ?? selectedPipeline` 配对逻辑本身正确），而在编辑器的 effect 依赖键。
- 改前实测佐证：详情接口 200 且 `nodes.length=3`，而画布 `.react-flow__node = 0` → 接口正常、缺陷在前端渲染。

**修复**：依赖键改为内容签名 `loadedDefKey = id#nodes.length`；并补「新建态（无 id）/ 空定义」清屏分支，防止残留上一条管道内容。

**回归矩阵（真实 id 路径）**：

| 场景 | `.react-flow__node` | 判定 |
|:--|:--|:--|
| 点选首条管道（前） | 0 | 复现缺陷 |
| 点选首条管道（后） | **3** | ✅ 已修复 |
| 连续切换第 2/3/1 条管道 | **3 / 5 / 3** | ✅ 与各自定义一致，无残留、无串台 |
| 点「新建」 | **0 / 0** | ✅ 清屏生效 |

### 2.4 非功能项

| 项 | 判定 |
|:--|:--|
| 主题令牌（铁律 §4.1） | PASS — 改动样式全部走 `styles.*`，无新增硬编码 Tailwind 颜色 |
| API 只增不改（铁律 §1.1） | PASS — 未改任何路径/参数签名/返回体；未改鉴权/滤波器 |
| 依赖注入/模块边界 | PASS — 纯前端组件内改动，未跨层 |
| console / network | PASS — console errors = 0；`/api/` 4xx-5xx = 0 |
| 既有 E2E 回归 | PASS — `lineage-smoke.mjs` A1~A6 全 PASS，13 次 API 全 200 |

---

## 三、验证证据

### 3.1 浏览器 E2E（隔离上下文执行，不干扰用户会话）

- 侧边栏标签与顺序：`['数据源连接','数据管道','数据质量','数据血缘','引擎配置']`
- 侧边栏区块顺序：`[数据工作台 + 4 主菜单]` → `[引擎配置 → 物理数据监控仪表 → 外部数据接口]`
- 画布回归：见 §2.3 矩阵
- `引擎配置` Tab 可达：渲染成功、无 ErrorBoundary
- console errors = 0、`/api/` 4xx-5xx = 0

> 说明：本轮验证初选 Chrome DevTools MCP 共享标签页，期间检测到该标签被外部会话切至 `#/ontology_workbench` 并落入 ErrorBoundary，为免干扰用户会话改用**隔离上下文新页面**完成；Playwright MCP 因浏览器二进制缺失（`chromium_headless_shell-1200` 未安装）不可用，未执行任何安装动作。

### 3.2 E2E 冒烟

```
PASS A1 无 ErrorBoundary 且工作台已渲染
PASS A2 血缘 Tab 存在且激活
PASS A3 单表查询回填 lineagetable
PASS A4 血缘接口请求成功（/engine/data/lineage/topology 200 ×2）
PASS A5 console 无 error
PASS A6 无意外 4xx/5xx
血缘地图 E2E: PASS
```

---

## 四、门禁判定

| 门禁 | 结果 | 说明 |
|:--|:--|:--|
| P0_GATE | **PASS** | 0 项 |
| P1_GATE | **PASS** | 0 项（阈值 3） |
| SECURITY_GATE | **PASS** | 纯前端 UI/渲染改动：未新增端点、未改鉴权、未改数据访问路径 → 攻击面零变化；无新增凭据/敏感数据处理 |
| ARCH_GATE | **PASS** | 无新模块/新容器；未违反分层、主题令牌、i18n、依赖注入铁律 |

**`deliverable_allowed = true`** ｜ `status = AUTO_CLOSED` ｜ 首轮即 PASS，无修复循环

---

## 五、缺陷清单

| ID | 级别 | 描述 | 是否本批次引入 | 处置 |
|:--|:--|:--|:--|:--|
| DW-304 | P3 | `loadedDefKey` 以 `nodes.length` 为签名粒度；若同一 `id` 下重取详情且节点数不变但内容变化，画布不刷新 | 否（当前无该代码路径） | 登记；若后续新增「同管道详情重取」须细化为 `id#nodes.length#updatedAt` |
| DW-305 | P3 | 页面标题类 key（`dw.txt.102168` 数据源与物理连接、`dw.txt.1b23b5` 数据质量健康检测、`dw.txt.0f541b` 全链路数据血缘地图）仍为旧长文案 | 否 | 用户仅要求菜单名，未改；如需统一须另行确认 |

**本批次引入缺陷：0**（P0=0 / P1=0 / P2=0）

---

## 六、可追溯性

| 需求/条目 | 出处 | 结论 |
|:--|:--|:--|
| DW-301 菜单改名 | 用户指令 ① | 正面交付 |
| DW-302 引擎配置靠底 | 用户指令 ② | 正面交付 |
| DW-303 画布加载修复 | 用户指令 ③ | 正面交付 |
| DW-304 内容签名粒度 | 需求③ 残值 | P3 登记 |
| DW-305 页面标题旧长名 | 需求① 副产品 | P3 登记 |

---

## 七、非阻塞建议

1. `loadedDefKey` 若后续需要支持「同一管道详情重取」，改为 `id#nodes.length#updatedAt` 更稳。
2. 页面标题类 key 与菜单名统一（需用户确认后再改，避免超出本次指令范围）。
3. **上一批次遗留全部保持开放**（不属本批次范围）：
   - S-204（P3）：`ecos-tests/lineage-smoke.mjs` 硬编码 `admin/admin123` 且打印 token 前缀 → 建议外置环境变量。
   - Wave 2 permitAll 收敛清单（前置：内部调用鉴权机制，覆盖 `KnowledgeHealthAggregator` / `SearchOntologyGraphTool` / `ExecuteActionTool`）；`/api/v1/chat/stream` 为 `EventSource`，须先定 token 传参方案。
   - `ClearanceInterceptor` / `ClearanceMvcConfig` 的 `/api/v1/engine/**` blanket 豁免未同步收敛（T-202）。
   - `#/ontology_workbench` 实体属性端点 404（既有缺陷，与本次无关）。

---

**审查人**: PM/Reviewer（`reviewer-code-review` v3，直派模式）
**审查时间**: 2026-09-17T06:20:00+08:00
**审计留痕**: `docs/plans/merge-audit-v2.1-alpha.md` §12.11