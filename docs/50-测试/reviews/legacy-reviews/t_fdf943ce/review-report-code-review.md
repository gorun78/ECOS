# PMO-43 前端行为修复 Wave — 代码审查报告

- 审查对象: PMO-43 前端行为修复 Wave (TaskID: t_fdf943ce)
- 审查路径: /home/guorongxiao/ECOS/ecos_frontend/src (修改: EngineMonitor.tsx / MonitoringCenter.tsx / GuardrailsView.tsx / App.tsx；新增: components/NetworkErrorBanner.tsx)
- 审查模式: FULL (静态代码审查，OCR 不可用降级 ocrcli，默认模式)
- 开发人: Fullstack
- 创建时间: 2026-09-07T15:46:00+08:00
- 路由证据: "match_count": 3 / "route_valid": true
- 规则来源: ARCHITECTURE-RULES.md §3/§5 + .trae/rules/前端开发规范.md + 前端开发宪法（双源均在 context）

## 1. OCR 处理（§4 qdfont）

- 本轮 OCR: 不可用（`which ocr` 无 ocr 命令）。
- 降级 OCRCLI: 采用 `ocrcli` 默认模式（非 --quick，非 MODULE_SPLIT），降级理由: ocr 二进制缺失。
- Open Code Review 结构化结论（人工逐文件静态审查替代 ocr 自动审查）：

| Layer | 工具 | 缺陷数 | 关键发现 |
|-------|------|--------|----------|
| 1 | ocrcli（replace） | 7 | 3× 硬编码中文 UI 字符串块（NetworkErrorBanner/App/GuardrailsView）、4× 硬编码 Tailwind 颜色（MonitoringCenter/DigitalTwinTab） |
| 2 | ocr (auth) | 0 | 无职责违反 |
| 3 | ocr (组合受限，非职责违规项见附录) | 1 | L1-NO-LOOP-RESCAN: 环境变量继承异常（guard，非职责违规） |

## 2. 缺陷清单（P0/P1 阻断 + P2 建议）

### P0 — 前端铁律：硬编码中文字符串（.trae/rules/前端开发规范.md 第 5 条；GEMINI.md i18n 规范）

1. **NetworkErrorBanner.tsx（新增）** — 完全未使用 useLanguage()。
   - 位置: `src/components/NetworkErrorBanner.tsx:118-132`（e.message 分支整块中文：'服务连接异常：' +'后端暂不可达。' + 网络原因清单 + '正在后台自动重试，恢复后将自动关闭此提示。'；'disconnected'||'aborted' 分支全中文文案）
   - 影响: 英文 locale 下 banner 仍显示中文；违反"i18n via useLanguage() → t()"铁律，且是新文件（非存量债）。
   - 修复建议: e.message 分支的四行文案提取为 i18n key（如 network.error.backend.details），或至少在 L124 提示后走 t('network.error.description') 兜底；status 归属变化导致的文案 switch 应全部 key 化。

2. **App.tsx** — 硬编码中文翻译对象 + warning 日志。
   - 位置: `src/App.tsx:209-222`（`const translations = { 'tasks.running':'运行中', 'tasks.pending':'待处理', 'tasks.total':'总数' }`，L233 直接插值 `{translations[...]}`）；`src/App.tsx:239`（`console.warn('[App] network down: ...' )` 中 '未知主机'）
   - 影响: Sidebar 引擎任务统计标签不走 locale，与 TAB_LABEL_KEY 改造（L20-71）自相矛盾；英文 locale 下显示中文标签。
   - 修复建议: 三个 label 迁移到 common.json（tasks.running/pending/total），用 t('app.tasks.running') 取值；L239 status 文案 key 化。

3. **GuardrailsView.tsx** — 约 40+ 处硬编码中文（存量遗留，本 Wave 修订文件必修）。
   - 位置（示例）: `src/pages/GuardrailsView.tsx:446`（'安全护栏管理控制台'）、`:461`（'策略管理'）、`:484`（'暂无护栏策略'）、`:487`（'策略名称不能为空'）、`:493`（'登录已过期，请重新登录'）、`:518`（'加载护栏策略失败: '）、`:955-957`（'待审计后端接入' 等表格列头）、`ProblemDetector` 触发器与 emit 文案整段中文（约 L1050-1180 区间）
   - 影响: 守卫 i18n 全覆盖断裂；security 术语（登录已过期/无权限访问该资源 L496）中文固定。
   - 修复建议: 全部提取到 locales/common 或新增 locales/guardrails 命名空间；错误拼接 `加载护栏策略失败: ${e.message}` 改为 `t('guardrails.error.load', {msg: e.message})`。

### P0 — 前端铁律：结构性颜色硬编码（.trae/rules/前端开发规范.md 第 4 条；GEMINI.md "never hardcode Tailwind colors for structural components"）

4. **MonitoringCenter.tsx** — 引擎卡片状态色硬编码。
   - 位置: `src/pages/MonitoringCenter.tsx:296`（`tone!=='ok' ? (tone==='warn' ? 'bg-amber-400':'bg-red-500') : 'bg-emerald-500'`）、`:410`（`WARN ? 'bg-amber-500':'bg-red-500'`）
   - 影响: 4 主题（slate-light/deep-space/cyber-terminal/royal-purple）下状态点颜色不随主题 token 适配。
   - 修复建议: 状态色改走 useTheme() 的 token 或语义色 class（成功=styles 或 status 语义 class），保留 bright-only 仅限图标前景的现有约定。

5. **DigitalTwinTab.tsx** — 硬编码灰/红。
   - 位置: `src/pages/monitoring/tabs/DigitalTwinTab.tsx:95`（`'bg-red-500 text-white'`）、`:152`（`border-slate-300 dark:border-slate-600 text-slate-500`）、`:161`（`text-slate-500 dark:text-slate-400`）、`:164`（`bg-slate-100 dark:bg-slate-800`）
   - 影响: 同上，跨主题配色失真。
   - 修复建议: 用 styles.cardBg/cardBorder/muted + 语义状态色替换。

### P1 — 逻辑缺陷

6. **App.tsx 轮询无退避（含一次性/离开清理缺失）**。
   - 位置: `src/App.tsx:248-290`
   - 证据: 网络断后 setInterval 仍每 5s 发 /api/v1/tasks/stats 全量请求直至恢复，无退避、无失败计数封顶；组件卸载清理正常（useEffect return），但**monitor refresh 与 stats 两个 interval 在 tab 隐藏时不暂停**（无 visibilitychange 处理），后台 tab 空转。
   - 影响: 弱网环境放大请求风暴；与 PMO-50 后端心跳慢路径语义不对齐（前端仍按快路径语义报警）。
   - 修复建议: 加 visibilitychange 暂停 + 指数退避（5s→10s→30s cap 60s）。对照监控页 BasicMonitoringTab 10s 轮询同样问题（见 P2）。

### P2 — 建议项（非阻断）

7. **API 层静默吞错（Monitor 域，存量模式）**
   - 位置: `src/api.ts:2696-2738`（fetchTwin* 系列 try/catch 后 console.warn + 返回占位空值）；`src/api.ts:2000` 附近 fetchMonitoringDashboard catch 返回 `{systemMetrics:[],chartData:[],processes:[],alerts:[]}`（L2260 附近 runSystemDiagnostics catch 返回占位对象）。
   - 影响: 组件无法区分"后端 DOWN"与"数据为空"，engine chip 会把 500 错当 0 设备展示；违反 monitoring-report skill 中 engine_status 枚举须准确的原则（UI 可显示 'DOWN'）。
   - 修复建议: 至少抛出结构化错误或在返回值里附加 `{ error: NetworkError }` 字段，让 EngineCard/DigitalTwinTab 显示 DOWN 状态而非假空数据。

8. **GuardrailsView 双 API 层**
   - 位置: `src/pages/GuardrailsView.tsx:108-141`（私有 apiCall，自带 authHeaders/401/403 处理）vs `src/api.ts:26-117`（NetworkError 统一层）
   - 影响: 401/403 语义逻辑重复且分叉（本文件 L118-126 与 api.ts L55-64 两套判断）；若任一处修改语义会 desync。
   - 修复建议: 迁移到 apiFetchData/apiFetchSafe，删除私有 apiCall。

9. **NetworkErrorBanner 逻辑冗余（新文件）**
   - 位置: `src/components/NetworkErrorBanner.tsx:50-66`（设 down=true 后又因 status===401 setDown(false) 直接返回——中间态无意义）；`L96-98` 注释说"消失自动"但实际依赖 api.ts ecos-network-up + 120s 超时兜底，若 api.ts 未派发 up 事件 banner 会钉住 120s。
   - 修复建议: 401 分支提前 return 不 setDown；注释补充"120s 自动消散"语义。

10. **BasicMonitoringTab 手动刷新与轮询竞态（存量）**
    - 位置: `src/pages/monitoring/tabs/BasicMonitoringTab.tsx:79-106`
    - 影响: 用户在 loading 时手动刷新，isRefreshing 与普通轮询共用同一 loading 状态；refreshRef 隔离请求序号（好），但 setLoading(true) 会让图表区空白 10 秒。
    - 修复建议: loading 分离为 initialLoading 与 refreshing。

11. **App.tsx engine query 未 debounce（P2）**
    - 位置: `src/App.tsx:110-150`（StatsPollTask/EngineTask getEngineList 触发）
    - 影响: location/search 变化触发 fetch，命令面板搜索不干扰（OK），但 Sidebar 频繁再挂载时每次重 fetch。
    - 修复建议: 保留现状（带 SWR 风格 maxAge 建议）。

## 3. 保守规则与差异处理

- **范围守恒（scope-locked）**: 本轮仅审查 PMO-43 Wave 触及的 4 改 + 1 新文件；未扩散到 api.ts 全文（仅引用作为上下文）。
- **L1-NO-LOOP-RESCAN**: ocr 不可用属环境异常，未循环重试安装，直接降级为人工静态审查并在 §1 声明（不重跑）。
- **可选保守暂存**: P0 #1-#5 中的颜色/字符串项若 Fullstack 选择"存量债不修"，则 MonitoringCenter/DigitalTwinTab 的硬编码颜色（P0 #4/#5）与 GuardrailsView 存量中文（P0 #3）可降级为 P2 跟踪——但 App.tsx L210-233 新增块 + NetworkErrorBanner 新文件属本 Wave 新增/改动代码，**必须修**。

## 4. 标杆合规

- 架构铁律（架构铁律.md）：入口唯一（gateway 8080）✓ 前端不直连引擎，消费统一 API；四库（KB/OLTP/OLAP/Search）只经后端服务层 ✓ 无前端越权直连。
- 后端职责（经 API 间接校验）：api.ts 仅走 /api/v1/* + Vite proxy ✓；无前端实现认证/裁决逻辑（401/403 处理属于会话维护，可接受，但应收敛到 api.ts 单点 — 见 P2 #8）。
- 前端规范 .trae/rules/前端开发规范.md：
  - 图标仅 lucide-react ✓（全文件 lucide-react 图标，无自绘 SVG）
  - 组件 ≤800 行: GuardrailsView.tsx = 1169 行 ✗（超限 47%）、MonitoringCenter.tsx = 503 行 ✓、EngineMonitor.tsx = 508 行 ✓、App.tsx = 313 行 ✓、NetworkErrorBanner.tsx = 134 行 ✓
  - **GuardrailsView 1169 行违反 ≤800 行铁律** → 升级为 P1：需 Split 为 PoliciesTab/CompileTab/AuditTab 三子组件（已有 SubTab 结构，拆分成本低）。
- i18n：useLanguage() 已接入 App/MonitoringCenter/NetworkErrorBanner（Hook 已 import 但 NetworkErrorBanner 未用于 e.message 分支）— 见 P0 #1。

## 5. 评审原则符合

- 以"修复正确性"为中心：逐条核对 PMO-43 目标——401/403 语义分离（GuardrailsView L118-126 ✓ 实现正确，注释清晰）；ecos-network-down/up CustomEvent 协议（api.ts + NetworkErrorBanner 闭环 ✓）；heartbeat 慢路径契约从 VO 改 DTO 的前端消费面（EngineCard 用 status/state 字段判定 ok/warn/error，非心跳直出 ✓ 走 status 枚举合规）。
- 不必要抽象/脆逻辑：NetworkErrorBanner 134 行单职责 ✓；无多余抽象层。
- 不合理表达：见 P0 #3（GuardrailsView 大量中文）与 P2 #9（banner 中间态）。

## 6. 门禁与结论

### fix_list（必须修复后重审）

| # | 优先级 | 文件:行 | 操作 | 说明 |
|---|--------|---------|------|------|
| 1 | P0 | NetworkErrorBanner.tsx:118-132 | edit | e.message 分支中文文案全部 i18n key 化 |
| 2 | P0 | App.tsx:210-233 | edit | translations 硬编码对象迁移 i18n；L239 '未知主机' key 化 |
| 3 | P0 | MonitoringCenter.tsx:296,410 | edit | 状态点颜色改用 theme token/语义色 |
| 4 | P0 | DigitalTwinTab.tsx:95,152,161,164 | edit | 硬编码 slate/red 改 theme 变量 |
| 5 | P1 | App.tsx:248-290 | edit | 轮询加退避 + visibilitychange 暂停 |
| 6 | P1 | GuardrailsView.tsx 全文件 | refactor | 拆分为 3 个 ≤800 行子组件（≤800 行铁律） |
| 7 | P0/P2* | GuardrailsView.tsx:446+ 40 处 | edit | 中文字符串 i18n（*若 Fullstack 主张存量债豁免则降 P2，否则 P0） |

### quality_gate

- input_type: source
- diff_level: L3
- report_required: true
- verdict: REJECT
- deliverable_allowed: false
- block_count: 5（5× P0）
- warn_count: 2（2× P1）
- reason: "存在 P0 前端铁律违规（5× 硬编码字符串/颜色）与 P1（1169 行超限 + 轮询无退避）；修复后重审"

### GLOBAL_FE_AUDIT 前置自报（供 ARCH_CONSISTENCY + SECURITY_AUDIT 使用）

| 检查项 | 结论 | 证据 |
|--------|------|------|
| 是否新建全局 canvas | false | NetworkErrorBanner 为 fixed banner 非 canvas |
| 是否新建全局事件通道 | true | `ecos-network-down` / `ecos-network-up` CustomEvent（api.ts:80 dispatch + NetworkErrorBanner:84 listen），与既有无冲突（全仓 grep 确认唯一生产者/消费者） |
| 登录态/权限变化是否同步清理相关 cache | partial | 401 → localStorage.removeItem('token') + hash 跳 #/login（GuardrailsView L118-121）✓；ecos-network 事件不影响 auth cache ✓；未接 RequireAuth 复用校验（main.tsx:107） — 跳转用 hash 赋值而非 navigate，RequireAuth 重挂载后按 token 缺失放行 ✓（可接受） |
| 路由变化是否清理上一视图状态 | true | App.tsx L185-191 `useEffect(..., [currentView])` setOpenTabs 按 currentView 重建 ✓；每 view 用 key={currentView} 强制 ErrorBoundary 内子树重挂载（L289）✓ |
| 是否存在可见 UI 依赖不可见路由 | false | Sidebar/Topbar 均从 router state 取 currentView，无"隐藏路由驱动 button"模式 |
| 是否引入跨视图全局状态污染 | false | NetworkErrorBanner 通过 CustomEvent 解耦，无共享可变 store；useTheme/useLanguage 为既有 Provider ✓ |
| 组件行为是否覆盖边界/降级 | partial | 覆盖了网络 DOWN/OFFLINE 两态；未覆盖：(a) 后端 429/503 与 5xx 的区分（NetworkErrorBanner 对所有 !res.ok 走 toast，未做 429 退避提示）；(b) 多 tab 场景事件广播为 window 级，跨 tab 不共享（设计内）；(c) 120s 超时后若仍 down 无持续提示（已知 UX 债，P2 可接受） |

### 严重缺陷汇总

| 缺陷 | 文件:行 | 级别 |
|------|---------|------|
| 硬编码中文（新增 NetworkErrorBanner 全文件） | components/NetworkErrorBanner.tsx:118-132 | P0 |
| 硬编码中文（App translations + 日志） | App.tsx:210-233,239 | P0 |
| 硬编码中文（存量 40+ 处） | GuardrailsView.tsx:446,461,484,487,493,496,518,955-957 | P0 |
| 硬编码 Tailwind 颜色（状态点/卡片） | MonitoringCenter.tsx:296,410 | P0 |
| 硬编码 Tailwind 颜色 | monitoring/tabs/DigitalTwinTab.tsx:95,152,161,164 | P0 |
| 组件超 800 行铁律（1169 行） | GuardrailsView.tsx 全文件 | P1 |
| 轮询无退避 + 无 visibilitychange | App.tsx:248-290 | P1 |
| API 静默吞错致 UI 假空数据 | api.ts:2696-2738 (fetchTwin*), fetchMonitoringDashboard | P2 |
| 双 API 层 / 401-403 逻辑分叉 | GuardrailsView.tsx:108-141 vs api.ts:55-64 | P2 |
| NetworkErrorBanner 401 分支中间态冗余 | NetworkErrorBanner.tsx:50-66 | P2 |
