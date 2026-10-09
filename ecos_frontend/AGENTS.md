# ecos_frontend — AGENTS

> 属主：详细设计-07（场景层 workspace 场景工作台）前端侧。本文件由 F07-25 建立，固化前端交付铁律，供后续 Wave 与静态门禁引用。技术栈：React 19 + Vite 6 + TypeScript + Tailwind 4 + HashRouter（URL 一律 `#/…`）+ Express BFF（`server.ts`）。

## 1. 铁律清单（门禁可校验）

1. **主题**：颜色**只**经 `useTheme()` 语义 token（`styles.appBg / cardBg / cardBorder / accentText / successText / warningText / dangerText …`，共 4 套主题 slate-light / deep-space / cyber-terminal / royal-purple）。**禁**硬编码 Tailwind 颜色类：`bg-white` / `bg-slate-*` / `text-gray-*` / `text-slate-*` / `bg-zinc-*` / `border-slate-*`。
2. **文案**：一律 `useLanguage()` 的 `t('ns.key')`，命名空间含 `scenario.*` / `cognition.*` / `workspace.*` 等；**禁**内联双语 `tl(zh,en)` 助手与硬编码中文串（注释除外）。i18n 为扁平点分 key JSON，位于 `src/locales/{ns}/{zh-CN,en}.json`，在 `components/LanguageContext.tsx` 合并注册。
3. **图标**：仅 `lucide-react`（经 `components/LucideIcon`）。**禁**裸内联 `<svg>`；图表内联 svg 统一登记在 `CognitionPanel#ForecastChart`（卷数 `REGISTERED-FOR-VOLUME-08`），其余页复用。
4. **体积**：单组件文件 ≤ **800 行**；超限须拆分。
5. **移动适配**：每个屏级组件用 `useMediaQuery('(max-width: 767px)')`（及 `useMobileSidebar` / `MobileDataTable` 等既有 hook/组件）做响应式。
6. **Git UI**：一律走 `services/gitService.ts` 单一通道，**只传 `repositoryId`**，禁前端自造 commit / 假 push 动画 / `localStorage` 伪持久化。
7. **无假真理（P0）**：**禁** `Math.random()` 伪造 hash、`setTimeout` 伪造同步、`toast('success')` 掩盖本地状态、硬编码身份（如固定人名/角色/`clientIp`）、认知失败本地合成 `verdict/answer/groundedDocs`。认知结论**只**来自 `useCognition`（→ `services/cognitiveClient.ts`）；503 只渲染横幅 + traceId，绝不掩成 200（F07-12/F07-22）。
8. **六态矩阵（§3.3）**：每个屏覆盖 空 / 加载 / 错误 / 边界 / 权限 / 离线 六态。
9. **类型**：禁 `: any` / `as any` / `<any>`；对外数据用 DTO interface 或 `unknown` + 窄化。
10. **日志**：禁裸 `console.*`；统一经 `services/logger.ts`（dev 才 debug/info，warn/error 恒发）。

## 2. 场景工作台三层 IA（F07-06）

- `pages/workbench/index.tsx` — L1「我的场景」：场景卡片区 + 快捷入口 + 高级视图折叠区。首屏**不出现** Mind / Goal Tree / Causal 等技术概念或后端原始字段名。
- `pages/workbench/[scenarioId]/index.tsx` — L2 场景详情：关键指标 + 认知摘要（`useCognition`）+ 最近动作。
- `pages/workbench/[scenarioId]/forecast.tsx` — L3 预测与推演：hypotheses/beliefs 只读渲染，503 → 横幅。
- `pages/workbench/NotFoundWorkbench.tsx` — 404 兜底，挂 `main.tsx` 的 `*` 路由。
- 路由注册于 `main.tsx`：`/workbench`、`/workbench/:scenarioId`、`/workbench/:scenarioId/forecast`（App.tsx 为布局壳 + `<Outlet/>`，路由表在 main.tsx）。
- 技术视图 `pages/ScenarioManagementView.tsx`（`#/project_workbench`）保留为「高级视图」，与工作台的 `workspace.*` 命名空间**不混用** `scenario.*` 技术视图键。

## 3. 工具与校验

- `npm run lint` = `tsc --noEmit`（全量类型检查；新文件须使其通过）。
- 体积门禁：`npx vitest run src/pages/scenario/__tests__/ComponentSize.test.ts`。
- 无假真理门禁：`src/pages/__tests__/FrontendStateMatrix.test.tsx` + 静态 grep（`Math.random` / 硬编码身份 / `api/v1/knowledge/query` / `console.` / `:any` / 硬编码颜色类在受治文件命中数须为 0）。

## 4. 禁区

- 不改 `pom.xml` / Java / 网关 / DDL（属后端册属主）。
- 不 `npm run dev` / `npm run build`（本 Wave 仅静态校验 + 单测）。
