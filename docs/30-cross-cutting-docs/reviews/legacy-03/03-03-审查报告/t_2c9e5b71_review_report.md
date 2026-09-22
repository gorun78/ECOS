# REVIEW_REPORT — 数据质量入口收敛与规则中心同源批次（t_2c9e5b71）

> 审查对象：`release/v2.1-alpha` 追加批次 2 个 commit
> - `2f22c55` `feat(数据工作台): 数据质量菜单直跳数据质量中心并移除质量检测页`（7 files, +27/-457）
> - `b9ed62d` `feat(数据质量): 规则中心统一读取 ecos_dq_rule_v2 真实规则并收敛为只读`（4 files, +121/-416）
>
> 审查范围：`git diff 70bd48f..HEAD` = **11 files changed, 148 insertions(+), 873 deletions(-)**
> 审查模式：**L2 标准**（全量）
> 审查日期：2026-09-16
> 审查技能：`reviewer-code-review` v3（交付门禁版）
> 关联审计：`docs/plans/merge-audit-v2.1-alpha.md` §12.12（本批次回填）
> 需求来源：用户直派需求 ①②（原文见 §三）

---

## 一、Step 0 — SOURCE_PATCH 前置校验（第一门禁）

| 校验项 | 结果 | 说明 |
|:--|:--|:--|
| SOURCE_PATCH 状态为 APPROVED | ⚠ **降级** | 本项目为**直派模式**（本环境不使用 Hermes 看板/swarm，无 `SOURCE_PATCH_APPROVAL_RECORD` 制品链） |
| 有批准记录 | ⚠ **降级** | 同上级；**但有用户原始需求原文 + 方案裁定**（需求①②见 §三，方案由用户裁定：直跳 + 删 HealthTab；规则中心统一读 legacy 端点 + 隐藏写 UI） |
| deliverable_allowed = true | ⚠ **降级** | 同上级 |
| PRD 引用 | 无独立 PRD 制品 | 需求源为用户直派口语需求（逐字见 §三），已结构化落于审计 §12.12.1 |
| OpenAPI 引用 | ⚠ 部分 | 本批次**不改任何后端 API 路径/参数签名/返回体**（纯前端）；消费端由治理端点 `GET /api/v1/dq/rules` 改为 legacy `GET /api/v1/ecos/dq/rules`（两者均为既有端点） |

**降级等价替代**（据实标注，不虚报）：以**独立复跑的前端编译门**（`npm run lint` → exit 0）+ **独立后端/DB 探针**（legacy 端点 200/total=26；治理端点 404；同 Controller 兄弟端点 200）+ **PM 的 V4 浏览器 E2E 证据**（本审查未独立复跑，见 §十 声明）替代上游批准记录。

→ 第一门禁以「降级通过」记录，**不阻断**审查。

---

## 二、Step 1 — 变更范围与夹带核查

`git diff --stat 70bd48f..HEAD`（本审查独立执行）：

| # | 文件 | 层 | commit | 变更性质 |
|:--|:--|:--|:--|:--|
| 1 | `ecos_frontend/src/pages/DataWorkbenchLayout.tsx` | 前端 | `2f22c55` | 35 ±：新增 `SideTabItem.navigateTo` 跨路由项 + `useNavigate`；健康 tab 渲染与 `AddHealthCheckModal` 挂载移除 |
| 2 | `ecos_frontend/src/pages/data-workbench/Modals.tsx` | 前端 | `2f22c55` | 41 ±：删除 `AddHealthCheckModal`（41 行）与文件头注释更新 |
| 3 | `ecos_frontend/src/pages/data-workbench/api.ts` | 前端 | `2f22c55` | 86 ±：删除 `DQ_RULES` 常量、`mapDqRule`/`mapDqStatus`/`mapDqCheckType`、`fetchDataHealthChecks`/`createHealthCheck`/`runHealthCheck` |
| 4 | `.../data-workbench/hooks/useDataWorkbench.ts` | 前端 | `2f22c55` | 71 ±：移除 `healthChecks`/`selCheckId`/`showAddCheck`/`nh*` 状态与 `createHealth`/`runCheck` |
| 5 | `.../data-workbench/lineage/types.ts` | 前端 | `2f22c55` | 14 ±：删除零引用 `DataLineageTabProps` |
| 6 | `.../data-workbench/tabs/HealthTab.tsx` | 前端 | `2f22c55` | **物理删除 224 行** |
| 7 | `.../data-workbench/types.ts` | 前端 | `2f22c55` | 13 ±：删除零引用 `DataHealthCheck` |
| 8 | `ecos_frontend/src/pages/data-quality/RuleCenterTab.tsx` | 前端 | `b9ed62d` | 477 ±：数据源切换 + 写操作 UI 全部移除 |
| 9 | `ecos_frontend/src/pages/data-quality/api.ts` | 前端 | `b9ed62d` | 56 ±：新增 `fetchEcosDqRules`（legacy 字段映射）；修复头注释 `*/` 提前闭合 |
| 10 | `ecos_frontend/src/locales/dw/zh-CN.json` | 前端 | `b9ed62d` | +2：`dw.dqRule.ruleTypeAll`、`dw.dqRule.searchPlaceholderName` |
| 11 | `ecos_frontend/src/locales/dw/en.json` | 前端 | `b9ed62d` | +2：同上（**双边齐备**） |

### 夹带核查结论：**无夹带**（独立核实）

| 核查项 | 命令 | 结果 |
|:--|:--|:--|
| 提交是否含后端改动 | `git show --name-only`（两 commit） | **11 个文件全部位于 `ecos_frontend/`**，无 `ecos_backend/`、无 `docs/`（文档留痕由本审查产出，非提交内容） |
| 提交是否含并发会话的 5 个 ontology 文件 | `git show --stat 2f22c55`、`git show --stat b9ed62d`（逐个提交核） | 两提交的 11 文件**均不含** ontology 5 文件 → 未被本批次夹带 |
| 提交消息与 diff 是否一致 | `git show 2f22c55`、`git show b9ed62d` | 一致（见逐文件变更性质表） |

> 工作区残留（**非本批次交付物**，属其他并发会话）：`ecos_frontend/src/locales/ontology/{zh-CN,en}.json`、`src/pages/ontology/ProposalPanel.tsx`、`src/services/ontologyApi.ts`、`src/types/ontology.ts`。本审查**未触碰**。

### ⚠ 审查期间 HEAD 前移（并发会话提交，**不在本批次范围内**）

本审查以 11 文件的批次告一段落后，重跑 `git status --porcelain` 发现 HEAD 已由 `b9ed62d` 前移至 `e480988`：

```
$ git log --oneline -4
e480988 fix(本体工作台): 修复提案状态大小写不一致导致的徽章渲染崩溃     ← 审查期间由并发会话提交
b9ed62d feat(数据质量): 规则中心统一读取 ecos_dq_rule_v2 真实规则并收敛为只读   ← 本批次
2f22c55 feat(数据工作台): 数据质量菜单直跳数据质量中心并移除质量检测页           ← 本批次
70bd48f docs(数据工作台): 补充三项改动审查报告与审计留痕 §12.11

$ git show --stat e480988   → 5 files（locales/ontology/{en,zh-CN}.json、pages/ontology/ProposalPanel.tsx、
                              services/ontologyApi.ts、types/ontology.ts），+73/-26
$ git diff --stat 70bd48f..HEAD  → 现为 16 files changed, 221 insertions(+), 899 deletions(-)
```

**处置**：`e480988` 正是本审查被明令**不得触碰**的 ontology 5 文件（其他并发会话的交付物），属**另一批次的范围**，本审查**不对其做门禁判定**。本报告的门禁结论**仅覆盖 `2f22c55` + `b9ed62d` 的 11 文件**（任务指定的批次范围，且与任务提示给出的 +27/-457 与 +121/-416 逐字吻合）。建议 PM 后续对 `e480988` 单独立项审查。

---

## 三、Step 2 — 需求落地核验（逐条）

用户需求原文（逐字）：

> 数据工作台：
> 1、点击数据质量直接进入数据质量中心，目前数据质量检测去掉；
> 2、数据质量中心的规则中心与现有数据质量页面中显示的规则保持同一数据来源

### 需求 ① — 「数据质量」直跳数据质量中心 + 去掉质量检测  → **达成**

| 验收点 | 证据（文件:行） | 判定 |
|:--|:--|:--|
| 侧边「数据质量」改为跨路由跳转 `#/dq_dashboard` | `DataWorkbenchLayout.tsx:54` `{ id: 'health', icon: 'ShieldAlert', i18nKey: 'dw.tab.health', navigateTo: '/dq_dashboard' }`；`:102` `onClick={() => tab.navigateTo ? navigate(tab.navigateTo) : setActiveTab(tab.id)}` | ✅ |
| 跨路由项不参与 tab 选中态（避免高亮串台） | `DataWorkbenchLayout.tsx:100` `const active = !tab.navigateTo && activeTab === tab.id;` | ✅ |
| 菜单标签仍为 4 项且键位复用 | `:51-56` `TAB_CONFIG` 仍 4 项；`dw.tab.health` 在 zh/en 双语均存（`dw/zh-CN.json:680`=数据质量 / `dw/en.json:675`=Data Quality） | ✅ |
| 质量检测页**物理删除**（非注释/非隐藏） | `HealthTab.tsx` 从版本库删除；本审查 `Test-Path` → `False` | ✅ |
| 该 tab 的渲染分支同步移除 | `DataWorkbenchLayout.tsx` 删去 `{activeTab === 'health' && <HealthTab .../>}`；`TabName` 联合类型收敛为 `'connections' \| 'pipeline-builder' \| 'lineage' \| 'engine-config'`（`:26`） | ✅ |
| 目标路由真实存在 | `main.tsx:133` `<Route path="dq_dashboard" element={<DataQualityDashboard />} />` | ✅ |

**过度实现核查**：无。未新增页面/未改路由表/未动其它 tab；`SideTabItem` 抽象仅服务于「跨路由项」这一既有需求（避免在 `TAB_CONFIG` 里塞入第二套渲染分支）。

### 需求 ② — 规则中心与数据质量页**同一数据来源** → **达成**

数据质量页（原 HealthTab）读取的常量即 `'/api/v1/ecos/dq/rules'`（commit `2f22c55` 删除前的 `data-workbench/api.ts` `const DQ_RULES = '/api/v1/ecos/dq/rules'`）。本批次规则中心改为同一端点：

| 验收点 | 证据 | 判定 |
|:--|:--|:--|
| 规则中心改读 legacy 同源端点 | `data-quality/api.ts:28` `const DQ_LEGACY_BASE = "/api/v1/ecos/dq"`；`:144-147` `fetchEcosDqRules()` → `${DQ_LEGACY_BASE}/rules` | ✅ |
| 底层读表一致 | 独立探针：该端点返回 26 条，首行 `{"id":"26","name":"w8","ruleType":"NOT_NULL","severity":"HIGH","enabled":true}`；DB `ecos_dq_rule_v2` = 26 行，分布 NOT_NULL 18/COMPLETENESS 3/VALIDITY 2/ACCURACY 1/UNIQUENESS 1/CONSISTENCY 1（与 PM 证据一致） | ✅ |
| 解包层次正确（双包装） | `apiFetchData`（`src/api.ts:255`）已解外层 `.data`；端点原始体 `{code:0,data:{data:[...],total:N}}` → `fetchEcosDqRules` 取 `resp.data`，同时兼容数组形态（`api.ts:148-149`） | ✅ |
| 不编造缺失字段 | legacy 无 `ruleCode/category/domain/targetKind` → 一律填空串；`enabled` → `ACTIVE`/`DISABLED`（`api.ts:150-164`） | ✅ |
| 写操作在治理端点就绪前隐藏 | `RuleCenterTab.tsx` 已无新建/编辑/审批/驳回/删除/废止/替代/版本历史入口；`api.ts:10-11`、`RuleCenterTab.tsx:8-9` 注释显式说明「保留待 Phase 2 复用」 | ✅ |

**过度实现核查**：无功能性越界。附带修复了 `api.ts` 头注释 `*/` 提前闭合导致的 tsc 语法错误（`b9ed62d` 提交信息自述）——该修复**属必要**（否则 tsc 不过，违反铁律 §5.1#11），非越界。

---

## 四、Step 3 — 安全审计（`reviewer-security-audit` 关注面）

| 检查项 | 方法 | 结果 |
|:--|:--|:--|
| 是否触碰后端鉴权三滤波器 | `git show --name-only` 两 commit | **PASS**：11 文件全为前端；`SecurityConfig.java` / `ClearanceInterceptor.java` / `VersionPrefixRewriteFilter.java` / `application.yml(auth.whitelist)` **零改动** → 未放宽/收紧任何鉴权面 |
| 是否引入敏感信息（密钥/凭据/连接串） | 增量行扫描 `git show \| Select-String '^\+' \| Select-String 'token\|localStorage\|…'` | **PASS**：新增行**无** token/key/password 字面量 |
| 是否打印 token / 凭据 | 同上（`console.log`） | **PASS**：新增行零 `console.log`；反而**删除**了 `data-workbench/api.ts` 中 3 处 `console.warn('[data-workbench] …failed:', e)` |
| 是否引入 XSS 面 | 新增行扫描 `dangerouslySetInnerHTML` / `<svg` | **PASS**：零命中；删除的 `AddHealthCheckModal` 亦无该风险 |
| 是否越权消费数据 | 端点变更语义 | **PASS**：legacy `GET /api/v1/ecos/dq/rules` 与治理端点同属既有鉴权门内端点，经 `apiFetchData` 注入 `Bearer`（`src/api.ts:233-235`），未新增匿名面 |
| §2.4-8 安全集成强制卡 | 本批次改动面 | **N/A**：不涉及敏感数据落库/密钥/第三方凭据；纯前端展示层与路由入口调整 |
| 敏感数据展示 | 规则列表列 | **PASS**：列收敛为 序号/规则名称/类型/严重度/状态；**移除了** `targetTable`/`targetField` 等可能含敏感字段名的列（`b9ed62d` 表头 diff） |

**结论：本批次为安全中性（无新增风险面），且在展示层做了收敛（减少字段暴露）。**

---

## 五、Step 4 — 架构一致性（`reviewer-arch-consistency` 关注面）

| 铁律 | 检查点 | 结果 |
|:--|:--|:--|
| §4.1 禁止硬编码 Tailwind 结构色 | 增量行扫描 `bg-white\|bg-slate\|bg-gray-\|text-gray-\|border-gray-\|#RRGGBB` | **PASS**：新增行零命中。`STATUS_META` 中 `bg-amber-50 … dark:bg-amber-500/10`、`bg-emerald-50 … dark:bg-emerald-500/10`、`bg-red-50 … dark:bg-red-500/10` 属**语义色允许写法**；`NEUTRAL_BADGE_CLS` 使用 CSS 变量 + `color-mix()` 主题感知 | ✅ |
| §4.2 图标仅 `lucide-react` | 增量行扫描自定义 SVG | **PASS**：零 `<svg`；`RuleCenterTab.tsx:15-30` 全部来自 `lucide-react` | ✅ |
| §4.3 禁止硬编码中文/英文串 + i18n 双边齐备 | 逐新增行核对 + 双语 key 存在性 grep | **PASS**：UI 文案全部 `t(...)`；新增 2 key（`ruleTypeAll`/`searchPlaceholderName`）在 `dw/zh-CN.json:1177,1181` 与 `dw/en.json:1172,1176` **双边齐备**；新增中文仅出现在 JSDoc/行注释（允许） | ✅ |
| §4.3 引用 key 必须可解析 | 逐 key grep 双语 | **PASS**：`RuleCenterTab.tsx` 引用的全部 `dw.dqRule.*`（含 `onlyReadPhase1`、`statusMachine.*`、`totalCount`、`retry`、`empty`、`loadFailed`、`colSeq/colName/colType/colStatus`、`status`、`severity`、`title`、`refresh`、`loading`、`statusAll`）在 zh/en 双语均存在 | ✅ |
| §4.8-1 列表/详情配对 | 是否直接消费列表摘要渲染编辑态 | **PASS / N-A**：规则中心本批次**无编辑态**（写 UI 已移除），不构成「摘要渲染编辑画布」风险 |
| §4.8-3 写后刷新 | 写操作移除 | **N/A**：无写操作；只读 `load()` + 手动「刷新」按钮（`RuleCenterTab.tsx:222-226`） |
| §4.8-2 枚举边界一致 | 是否自定义枚举 | **PASS**：`ruleType` 选项由**已加载数据动态派生**（`:154-157`），不再硬编码 `["NULL","UNIQUE",…]`（原硬编码清单已随 diff 删除）；状态筛选仅 `ACTIVE/DISABLED`，与 `enabled` 映射一一对应 |
| §5.1 #11 未过编译门的前端变更不得留在工作树 | `cd ecos_frontend; npm run lint`（`tsc --noEmit`） | **PASS**：本审查**独立复跑** → `LINT_EXIT=0`（见 §十） |
| §5.1 #12 前端直接消费列表摘要渲染编辑态 | 同 §4.8-1 | **PASS / N-A** |
| §5.1 #5/#10 API 只增不改 / 不加模块容器 | 全批次 | **PASS**：无后端改动、无新 Maven 模块、无新 Docker 容器、无新依赖 | ✅ |
| §5.1 #14 不创建临时脚本 | 本批次产物 | **PASS**：commit 内无脚本；本审查探测全部使用内联命令 | ✅ |
| 分层/依赖方向（§0.3/§0.3.1） | 前端内部 | **PASS**：仅页面与 hooks/api 层内部收敛，未新增跨层或跨模块依赖 | ✅ |

---

## 六、Step 2b — API 契约与死代码残留

### 6.1 API 契约（只增不改）

| 检查项 | 证据 | 结论 |
|:--|:--|:--|
| 既有 API 路径/参数签名/返回体是否被改 | 本批次 11 文件全为前端；`git show --name-only` 无后端 | **未改任何后端契约** ✅ |
| 前端消费的端点是否为既有端点 | `GET /api/v1/ecos/dq/rules`（既有 DqController）、`GET /api/v1/dq/rules`（既有 DqGovernanceController） | 两者**均为既有端点**，本次仅改「消费哪一个」✅ |
| 治理端点函数是否被删除 | `data-quality/api.ts` 仍导出 `fetchDqGovernanceRules(171)` / `fetchDqGovernanceRuleDetail(191)` / `createDqGovernanceRule(367)` / `updateDqGovernanceRule(385)` / `deleteDqGovernanceRule(398)` / `dqRuleAction(411)` / `fetchDqGovernanceVersions(445)` | **保留未删** ✅（与提交信息一致） |

### 6.2 死代码与残留核查（逐项独立 grep）

| 残留对象 | 期望 | 实测（`Grep` 全 `src`） |
|:--|:--|:--|
| `HealthTab` / `AddHealthCheckModal` | 零引用 | **No matches found** ✅（文件亦 `Test-Path=False`） |
| `DataHealthCheck` / `healthChecks` | 零引用 | **No matches found** ✅ |
| `fetchDataHealthChecks` / `createHealthCheck` / `runHealthCheck` / `mapDqRule` | 零引用 | **No matches found** ✅（删除前确为零引用：唯一消费方为已删 `HealthTab`/`useDataWorkbench`/`Modals`） |
| `DataLineageTabProps` | 零引用 | **No matches found** ✅ |
| `selCheckId` / `showAddCheck` / `nhName` / `nhDs` / `nhType` / `nhThr` | 零引用 | **No matches found** ✅ |
| `RuleCenterTab.tsx` 内治理写操作引用 | 零引用 | **PASS**：`crateDqGovernanceRule`/`RuleFormDialog`/`ConfirmDialog`/`RuleReviewDialog`/`VersionTimelineDrawer`/`showToastGlobal` 相关 import 与调用全部移除（diff 已核） |
| 未使用的 import | 零 | **PASS**：`RuleCenterTab.tsx:15-30` 逐个核对（`Clock/FileText/Fingerprint/GitBranch/Hash/Info/Layers/Loader2/RefreshCw/Send/ShieldCheck/Tag/X/XCircle`）均有使用；`Plus/Pencil/History/Trash2/Check` 已删除 |

---

## 七、Step 5 — 回归风险专项（第 6 项任务要求）

| 风险假设 | 实测/静态核验 | 裁定 |
|:--|:--|:--|
| **筛选恒空** | 修复为**前端内存过滤**（`RuleCenterTab.tsx:160-168`）：`ruleType` 下拉选项由数据动态派生（`:154-157`），不会出现「选项是硬编码枚举、数据里没有」的恒空；`status` 仅 `ACTIVE/DISABLED`，与 `enabled` 映射严格对应（DB 实测 `enabled=t` 26/26 → 全部 `ACTIVE`）；关键词按 `ruleName` 匹配（legacy 有 `name`） | ✅ **不恒空**（PM 证据：`共 26 条 · 当前显示 26 条`） |
| **`?table=` 深链恒空** | `RuleCenterTab.tsx:126-130` **有条件生效**：仅当返回结果中确有非空 `targetTable` 才启用过滤，否则忽略该参数。逻辑**正确**（避免恒空）；但因 `fetchEcosDqRules` 映射**不含 `targetTable`**（`api.ts:150-164`），`hasTargetTable` 恒 `false` → 深链**静默忽略**（不过滤、也不提示） | ⚠ **降级为「深链静默失效」**（P3-D1）：`CatalogContextMenu.tsx:130` 的「配置DQ规则」跳转不再产生过滤效果。属**有意降级**（替代方案为恒空列表，更差），已在提交信息与 api.ts 注释留痕，非阻断 |
| **状态机按钮点了报错** | 写操作 UI **已整体移除**（表格 `colActions` 列、`renderActions`、3 个 `ConfirmDialog`、`RuleReviewDialog`、`VersionTimelineDrawer`、`RuleFormDialog` 全部删除），**不存在可点击的写按钮** → 无报错路径 | ✅ **风险消除**（优于「隐藏但可点」的实现） |
| 只读提示是否可见 | `RuleCenterTab.tsx:229-232` 渲染 `t("dw.dqRule.onlyReadPhase1")` | ✅（PM 证据：提示可见；「新建规则」按钮不存在） |

---

## 八、缺陷清单（P0~P3 分级 · 带证据与修复建议）

### 缺陷汇总

| 级别 | 数量 | 本批次引入 | 说明 |
|:--|:--:|:--:|:--|
| **P0** | **0** | — | — |
| **P1** | **0** | — | — |
| **P2** | **1** | 0（既有后端缺陷） | 治理端点 404 真实根因未闭合（阻塞 Phase 2，不阻塞本批次） |
| **P3** | **5** | 3 | 静态可核实的小问题，均不阻断交付 |

---

### P0 —— 无

### P1 —— 无

### P2-1（既有后端缺陷，非本批次引入）治理端点 `GET /api/v1/dq/rules` 404 根因**未闭合**

- **文件**：`ecos_backend/engine/data-engine/data-engine-impl/.../quality/controller/DqGovernanceController.java:71`、`.../service/DqGovernanceServiceImpl.java:57-70`、`.../mapper/DqRuleMapper.java:44-53`、`ecos_backend/gateway/.../handler/GlobalExceptionHandler.java:193-202`
- **现象**：带 admin JWT 请求 `GET /api/v1/dq/rules` → HTTP 404，响应体 `{"code":404,"message":"端点暂未开放或服务未就绪，请稍后重试或联系管理员","success":false}`（= `handleAny(Exception)` 兜底文案，行 201）。
- **本审查**订正**了把「路由不可达」当作根因的表述，并把根因范围**收窄到确定性结论**（证据链见下）：

| 步骤 | 命令/文件 | 输出 | 排除了什么 |
|:--|:--|:--|:--|
| 1 | `GET /api/v1/dq/rules/dimension-registry`（同 Controller） | **200** + 6 维度数据 | 排除「组件扫描 / 路由未注册 / 三滤波器 403」 |
| 2 | `GET /api/v1/dq/rules/xyz` | **404** + `"DQ 规则 xyz 不存在"`（`NotFoundException` 处理器文案） | 排除「Controller/Service/Mapper Bean 不可用」——`findById` 已成功执行并返回 null |
| 3 | `GET /api/v1/dq/rules?pageSize=201` | **400** + `"DQ 规则列表 pageSize 上限 200"`（`DqGovernanceServiceImpl.java:61` 抛出） | **决定性**：证明 `listRules` **已进入方法体**，异常发生在其**之后**（即 mapper 查询段），与路由无关 |
| 4 | `docker exec ecos-postgres psql … \d ecos_dq.dq_rule` | 表存在，列齐全（含 `is_deleted`） | 排除「表缺失 / 列不匹配」 |
| 5 | 手工执行 `listByFilter` 生成的**完整 SQL** | `(0 rows)` 正常返回 | **排除「表 0 行导致 404」这一推测**（0 行应返回空页而非 404） |

- **剩余不确定性（据实标注）**：未能取得 gateway 控制台堆栈（无 logback 文件 appender，日志仅输出到启动终端），故**无法在本次审查内把根因钉死**到具体行。基于证据 2 vs 3 的差异，**唯一未排除的怀疑点**是 `DqRuleMapper` 的两个 `@SelectProvider` 方法（`listByFilter` / `countByFilter`）在运行期调用失败（典型为 provider 方法参数名解析 / `-parameters` 编译开关类问题），被 `handleAny` 统一兜底为 404。
- **影响**：规则中心**写操作 Phase 2 无法启动**；且 `handleAny` 把「服务端异常」与「路由不存在」映射为同一响应，**掩盖真实根因**（可诊断性缺陷）。
- **修复建议**：① 取 gateway 控制台堆栈定位（或为 gateway 补 `logback-spring.xml` 文件 appender）后修复 mapper 调用；② 建议 `handleAny` 对 `NoResourceFoundException`（真 404）与其它运行时异常分档（后者不宜返回 404）；③ 治理端点恢复后，规则中心按 `api.ts:6-11` 注释切换到治理端点并恢复写 UI（含回归「筛选/深链/状态机」三项）。
- **裁定**：**P2 非阻断**（本批次采用 legacy 端点已绕过；不属本批次引入的缺陷）。

---

### P3-D1（本批次引入的降级）`?table=` 深链静默失效

- **文件**：`ecos_frontend/src/pages/data-quality/RuleCenterTab.tsx:126-130`；`.../data-quality/api.ts:150-164`（映射缺 `targetTable`）；触发方 `src/pages/data-workbench/CatalogContextMenu.tsx:130`
- **证据**：`fetchEcosDqRules` 返回对象**不含** `targetTable` 键 → `arr.some(r => (r.targetTable ?? "").trim() !== "")` 恒 `false` → `initialTableFilter` 被忽略；URL 上的 `?table=xxx` 仍被 `DataQualityDashboard.tsx:93` 传入，但**不产生任何过滤与提示**。
- **修复建议**：在只读提示旁按 `initialTableFilter` 追加一行「当前数据源暂不支持按表过滤」（小改动、不引入恒空）；待治理端点恢复后自动生效。

### P3-D2（本批次引入的展示回归）严重度配色与实际取值不匹配

- **文件**：`RuleCenterTab.tsx:310-317`（`SeverityInline`）
- **证据**：映射仅识别 `CRITICAL`/`WARNING`/`INFO`，其余落 `successBg/successText`；DB 实测 `ecos_dq_rule_v2` 严重度为 **HIGH 21 / MEDIUM 3 / LOW 2** → **21 条 HIGH 显示为绿色（success）**，语义误导。
- **修复建议**：补 `HIGH→danger`、`MEDIUM→warning`、`LOW→info`（保留 `CRITICAL/WARNING/INFO` 兼容治理端点）。

### P3-D3（本批次引入的展示回归）类型图标映射与 legacy 枚举无交集

- **文件**：`RuleCenterTab.tsx:93-95`
- **证据**：`TYPE_ICONS` 键为 `NULL/UNIQUE/REFRESH/FRESHNESS/RANGE/FORMAT/CUSTOM/DEFAULT`，而 legacy 实际 `rule_type` 为 `NOT_NULL/COMPLETENESS/VALIDITY/ACCURACY/UNIQUENESS/CONSISTENCY` → **无一命中**，全部退化为 `FileText`；`DEFAULT` 为永不可达死键。
- **修复建议**：改键为 6 维度 `rule_type`，或直接按「维度」渲染图标。

### P3-D4（残留死代码）孤儿组件与预留 API

- **文件**：`ecos_frontend/src/pages/data-quality/RuleReviewDialog.tsx`、`.../VersionTimelineDrawer.tsx`、`.../api.ts:171,191,367,385,398,411,445`
- **证据**：全 `src` grep 显示两组件**仅自引用**（`RuleReviewDialog` 内部引用 `dqRuleAction`），无外部 importer；治理写/详情/版本 API 的**唯一引用方**即这两个孤儿组件。
- **说明**：PM 已在提交信息与 `api.ts:10-11` 显式声明「保留待 Phase 2 复活」→ **属有意保留**，登记不阻断。
- **修复建议**：Phase 2 落地时同步复活并回归；若 Phase 2 长期不启动，建议给这些导出加 `@deprecated` 注释，避免被误判为可用能力。

### P3-D5（残留孤儿 i18n key）

- **文件**：`ecos_frontend/src/locales/dw/zh-CN.json`、`en.json`
- **证据**：写 UI 与 `AddHealthCheckModal` 移除后，以下 key 在 `src/` 内**零引用**：`dw.dqRule.newRule`、`dw.dqRule.categoryAll`、`dw.dqRule.domainAll`、`dw.dqRule.searchPlaceholder`、`dw.dqRule.deleteConfirm`、`dw.dqRule.delete`、`dw.dqRule.form.*`，以及 `AddHealthCheckModal` 专用的 `dw.txt.209a45` 与 `databench.layout.toast.health*` 系列（grep 命中 0）。
- **说明**：i18n 冗余不违反铁律（§4.3 只要求 UI 不硬编码 + 双边齐备）；Phase 2 复活写 UI 时可复用，故**不建议现在删除**，仅登记。
- **修复建议**：Phase 2 决策后统一清理或复用（二选一）。

---

## 九、Step 6 — 质量门禁判定

| 门禁 | 判定条件 | 实测 | 结论 |
|:--|:--|:--|:--:|
| **P0_GATE** | P0 缺陷数 = 0 | **0** | **PASS** |
| **P1_GATE** | P1 缺陷数 ≤ 3 | **0**（开放 P2=1 / P3=5，均为登记遗留或非阻断小项） | **PASS** |
| **SECURITY_GATE** | 无 CRITICAL/HIGH 开放漏洞 | **0 个**；纯前端批次，未触碰三滤波器，无凭据/token 打印，展示层字段收敛 | **PASS** |
| **ARCH_GATE** | 无 P0/P1 架构违规 | **0 个**；§4.1/§4.2/§4.3/§4.8/§5.1#11/#12 全部正向满足（编译门独立复跑 exit 0） | **PASS** |

### `deliverable_allowed = true`

四项门禁全 PASS → `status = AUTO_CLOSED`，无需人工签字放行（按 skill 定义）。
**审查历史（诚实留痕）**：本批次**首轮即 PASS，无修复循环**。

---

## 十、独立验证证据（本审查实际执行的命令与真实输出）

> 声明：**§十-4 的浏览器 E2E 为 PM 证据（本审查未复跑）**，原因：审查约束禁止启动 dev server。其余各项均为本审查**独立执行**。未验证项一律标注，不臆造。

### 1) 变更范围与夹带（独立执行）

```
$ git log --oneline -3           → b9ed62d / 2f22c55 / 70bd48f
$ git show --stat 2f22c55        → 7 files changed, 27 insertions(+), 457 deletions(-)
$ git show --stat b9ed62d        → 4 files changed, 121 insertions(+), 416 deletions(-)
$ git diff --stat 70bd48f..HEAD  → 11 files changed, 148 insertions(+), 873 deletions(-)
$ git show --name-only 2f22c55 b9ed62d → 11 个文件全部 ecos_frontend/**（无 ecos_backend、无 docs）
$ git status --porcelain         → 【审查开始时】" M ecos_frontend/src/locales/ontology/{en,zh-CN}.json"
                                    " M ecos_frontend/src/pages/ontology/ProposalPanel.tsx"
                                    " M ecos_frontend/src/services/ontologyApi.ts"
                                    " M ecos_frontend/src/types/ontology.ts"   （并发会话，未夹带）
                                 → 【审查结束时重跑】上述 5 项已被并发会话提交为 e480988（HEAD 前移，见 §二）；
                                    工作区余 " M docs/plans/merge-audit-v2.1-alpha.md"（本审查追加 §12.12）
                                    与 " M ecos_backend/.../WarnLogBean.java" + 未跟踪的 V131__ecos_warn_log.sql、
                                    PMO-59 指令（均为其他会话，非本审查产物）
$ git branch --show-current      → release/v2.1-alpha
```

### 2) 编译门（独立复跑）

```
$ cd ecos_frontend; npm run lint
> react-example@0.0.0 lint
> tsc --noEmit
LINT_EXIT=0          ← exit 0，无输出（与 PM 证据一致）
```

### 3) 后端/DB 事实探针（独立执行 · admin JWT）

```
$ POST /api/v1/auth/login {admin/admin123} → 200，data.accessToken 获取成功

GET /api/v1/ecos/dq/rules                    → 200  {"code":0,"message":"ok","data":{"data":[{"id":"26","name":"w8","description":"","ruleType":"NOT_NULL","config":{},"severity":"HIGH","enabled":true,"createdAt":"2026-09-04T12:11:33.160427",…
GET /api/v1/dq/rules                         → 404  {"code":404,"message":"端点暂未开放或服务未就绪，请稍后重试或联系管理员",…}
GET /api/v1/dq/rules/dimension-registry      → 200  {"code":0,…6 维度…}
GET /api/v1/dq/schedules                     → 200  {"code":0,"message":"ok","data":[],…}
GET /api/v1/dq/rules/xyz                     → 404  {"code":404,"message":"DQ 规则 xyz 不存在",…}         ← 关键：handler 执行、mapper 可用
GET /api/v1/dq/rules?pageSize=201            → 400  {"code":400,"message":"DQ 规则列表 pageSize 上限 200",…} ← 决定性：listRules 已进入方法体

DB:
  select count(*) from ecos_dq.dq_rule      → 0        （表存在，列齐全）
  select count(*) from ecos_dq_rule_v2      → 26
  rule_type: NOT_NULL 18 / COMPLETENESS 3 / VALIDITY 2 / ACCURACY 1 / UNIQUENESS 1 / CONSISTENCY 1
  severity : HIGH 21 / MEDIUM 3 / LOW 2
  enabled  : t 26
  手工执行 listByFilter 完整 SQL（SELECT … FROM ecos_dq.dq_rule WHERE is_deleted=FALSE ORDER BY updated_at DESC LIMIT 20 OFFSET 0）→ (0 rows) 正常返回

运行态核实（排除「部署陈旧」）:
  gateway fat-jar mtime = 2026-09-16 20:55:59；java 进程 StartTime = 2026-09-16 20:56:16（jar 先于进程，非陈旧部署）
```

### 4) PM 侧 V4 浏览器 E2E（**引用，未独立复跑**）

侧栏 `["数据源连接","数据管道","数据质量","数据血缘","引擎配置"]`；点击「数据质量」→ `location.hash==="#/dq_dashboard"`；规则中心 `共 26 条 · 当前显示 26 条`、行数 20（`PAGE_SIZE=20`）、表头 `["#","规则名称","类型","严重度","状态"]`、前 3 行 `["1","w8","NOT_NULL","HIGH","生效"],["2","新规则","NOT_NULL","HIGH","生效"],["3","新规则","NOT_NULL","HIGH","生效"]`；中/英双语 raw key 泄漏 0；只读提示可见、无「新建规则」按钮；`consoleErrors: []`、`badResponses: []`。
—— 上述与本节 1)~3) 的独立结论**互相印证**（数据条数、首行字段、i18n key 齐备性、legacy 端点 200）。

### 5) 静态 grep（独立执行）

```
HealthTab|AddHealthCheckModal|DataHealthCheck|healthChecks|fetchDataHealthChecks|createHealthCheck
|runHealthCheck|mapDqRule|DataLineageTabProps|selCheckId|showAddCheck|nhName|nhThr|nhDs|nhType
  → No matches found
Test-Path ecos_frontend/src/pages/data-workbench/tabs/HealthTab.tsx → False
git grep 'dw.tab.health' -- ecos_frontend/src/locales → dw/en.json:675 + dw/zh-CN.json:680（双语齐备）
git show 2f22c55 b9ed62d | Select-String '^\+' | Select-String 'bg-white|bg-slate|bg-gray-|text-gray-|border-gray-|#RRGGBB'
  → 无输出（新增行零硬编码结构色）
git show … | Select-String '^\+' | Select-String 'console.log|localStorage|token|dangerouslySetInnerHTML|<svg'
  → 无输出
```

---

## 十一、未闭合遗留（登记，不阻断）

1. **P2-1** 治理端点 `GET /api/v1/dq/rules` 404 根因未钉死（怀疑 `@SelectProvider` 调用期异常被 `handleAny` 兜底）；**Phase 2 写操作的前置阻塞项**。
2. **P3-D1** `?table=` 深链静默失效（有意降级，待治理端点恢复）。
3. **P3-D2 / P3-D3** 严重度配色与类型图标映射未对齐 legacy 取值（纯展示层）。
4. **P3-D4 / P3-D5** 孤儿组件（`RuleReviewDialog`/`VersionTimelineDrawer`）与孤儿 i18n key —— 有意保留待 Phase 2，建议届时一并处置。
5. **PM 侧 V4 浏览器 E2E 未由本审查复跑**（约束禁止启动 dev server）；已用后端/DB 探针 + 静态 grep 交叉印证关键断言。
6. **`e480988` 未经审查**（审查期间由并发会话提交的 ontology 5 文件，`fix(本体工作台): 修复提案状态大小写不一致导致的徽章渲染崩溃`）—— 属另一批次范围，建议 PM 另立审查项。
7. 上一批次遗留项保持开放（不属本批次范围）：`lineage-smoke.mjs` 硬编码 dev 凭据、Wave 2 `permitAll` 收敛清单、`ClearanceInterceptor` blanket 豁免、`#/ontology_workbench` 实体属性端点 404。

---

## 十二、审查结论

| 项 | 结论 |
|:--|:--|
| 交付门禁 | **四门禁全 PASS，`deliverable_allowed = true`** |
| 需求① | **达成**：侧边「数据质量」跨路由直跳 `#/dq_dashboard`；`HealthTab.tsx` 物理删除（含 `AddHealthCheckModal`、`DataHealthCheck`、`healthChecks` 等全链清理，零残留） |
| 需求② | **达成**：规则中心改读 `GET /api/v1/ecos/dq/rules`（与数据质量页同源，读 `ecos_dq_rule_v2`，独立探针 total=26 与 PM E2E 一致），缺失字段留空不编造，写 UI 于治理端点就绪前隐藏 |
| 开放缺陷 | P0=0 / P1=0 / P2=1 / P3=5（P2 属**既有后端**缺陷，非本批次引入；P3 中 3 项为本批次引入的展示层小项，均可静态定位并给出修复建议） |
| 夹带无关改动 | **不存在**（11 文件全在 `ecos_frontend/`，且与两 commit 的声明范围一致；并发会话的 ontology 5 文件未被这两次提交夹带） |
| 审查边界 | 门禁判定仅覆盖 `2f22c55` + `b9ed62d`（11 文件）。审查期间并发会话另提交 `e480988`（ontology 5 文件）使 HEAD 前移，**不在本批次范围、未做门禁判定**，建议另立审查 |
| 安全 | 中性：未触碰三滤波器、无凭据/token 打印、无新增匿名面，展示字段反而收敛 |
| 编译门 | `npm run lint`（tsc --noEmit）**独立复跑 exit 0** |
| 追溯 | 需求①→`2f22c55`；需求②→`b9ed62d`；凭证回填 `docs/plans/merge-audit-v2.1-alpha.md` §12.12.4 |

**审查人**：PM/Reviewer（直派模式，`reviewer-code-review` v3）
**时间**：2026-09-16T22:30+08:00
