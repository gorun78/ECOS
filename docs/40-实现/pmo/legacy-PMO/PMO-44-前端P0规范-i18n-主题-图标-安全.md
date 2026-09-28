# PMO-44 前端 P0 规范项（i18n/主题/图标/安全 + 403 语义）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md) §4 前端铁律
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: fullstack-implementer (FE)
> 铁律:
> 1. 主题铁律（§4.1）：禁止硬编码 Tailwind 颜色，必须 `useTheme().styles`
> 2. i18n 铁律（§4.3）：禁止硬编码中文，必须 `useLanguage().t()`
> 3. 图标铁律（§4.2）：仅用 `lucide-react`，禁止自定义 SVG
> 4. 安全：`dangerouslySetInnerHTML` 必须 sanitize（XSS 拦截）

报告来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §5.1 P0 项 7,9,10,11,13,14 + §3 + §4.4/§4.6。

本批次 8 项 P0，分 5 Task（部分 Task 同时处理 2-3 个文件但属同一规范类，每 Task 单区间单规范）。

## §禁止清单

1. ❌ 不引入新主题/不修改 `ThemeContext`（4 主题固定）
2. ❌ 不允许 `v-if` + `v-for` 同节点（§1 React 规范）
3. ❌ 不允许 i18n namespace 泄漏（必须 `src/i18n/locales/{domain}/zh-CN.json` + `en.json` 双份）
4. ❌ DOMPurify：仅处理**白名单**（只允许 `<mark>`/`<span>`/`<code>`/`<strong>`），禁止全开
5. ❌ 不修改 utils.tsx / Request.ts 全局文件（除非 T5 403 语义必须的）

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos_frontend/src/pages/Login.tsx` | 整页改造：① 20+ 硬编码颜色 `bg-white`/`border-slate-200` → `styles.cardBg`/`styles.cardBorder`；② 7 处中文 `t("login.username")` 等（i18n namespace `login`，新增 keys: username/password/login/loading/fail/noNetwork/empty，zh-CN + en 双份）；③ 4 个自定义 SVG（`ChevronDown`/`Loader2`/`CheckCircle2`/`DollarSign`）替换 lucide-react 同名图标 | `npm run lint` 0 错误；`grep "bg-white\|text-slate-" src/pages/Login.tsx` = 0；grep 中文 = 0；4 主题切换无视觉异常；cn/en 切换后文案正确 |
| T2 | `ecos_frontend/src/pages/AIPKnowledgeView.tsx` | 整页改造（双缺失 useTheme + useLanguage）：① 7+ 硬编码颜色 → `styles.*`；② **56 处中文** → `t("aipKnowledge.*")`（新增 namespace `aipKnowledge`，zh-CN.json + en.json）；③ `src/main.tsx`/页面 `showToast={(type,msg)=>console.log(...)}` → 删（独立走 PMO-43 T2 已派） | `npm run lint` 0 错误；grep 中文字符在文件内 = 0；grep `bg-white` 等硬编码色 = 0；cn/en 切换后 RAG 页全部文案正确；`npm run build` 通过 |
| T3 | `ecos_frontend/src/pages/SystemConfigManager.tsx` + `src/components/TaskPanel.tsx` + `src/components/common/DataTable.tsx` | T3a `SystemConfigManager.tsx:347-380` `isZh ? '参数消耗审计' : '...'` 反 i18n → 改为 `t("systemConfig.paraAudit")` 走 `useLanguage()`（新增 namespace key）；③ `:351` 自定义 SVG 替换 lucide `X`；T3b `TaskPanel.tsx` 5 处 `alert()` (305,307,326,332) → `useToast().error()`；② `:317` `window.confirm` 批量删 → `ConfirmDialog`（**保留语义**，不引入新组件）；③ >500 行任务操作文案 → 抽公共 i18n `taskPanel.*` namespace；T3c `DataTable.tsx:165,189` `N 条记录`/`第 N/M 页` → `t("common.pagination.total", { n })` / `t("common.pagination.page", { p, m })` 参数化（公共组件违规，最高优先） | `npm run lint` 0 错误；`grep "是\|？\|\[" ... src/components/` 命中 0（除测试）；登录系统 → 数据表格切英文，"Total {n} records" 渲染；中文批量删 → 弹 ConfirmDialog 非 window.confirm |
| T4 | `ecos_frontend/src/pages/knowledge/components/ExtractionReviewPanel.tsx` + 全局 dangerouslySetInnerHTML 排查 | T4a `:171` `dangerouslySetInnerHTML={{ __html: highlightedSource }}` → `= DOMPurify.sanitize(highlightedSource, { ALLOWED_TAGS: ['mark','span','code','strong'], ALLOWED_ATTR: ['class','style'] })`（**白名单**）；安装/确认依赖 `dompurify`（若未安装则 `npm i -D dompurify` + Lock）；T4b 全仓 grep `dangerouslySetInnerHTML` → 输出命中清单到文档 `docs/PMO/PMO-44-XSS全仓扫描.md`（仅报告，不改；本指令只改 T4a 文件） | 单元 mock test：构造包含 `<script>alert(1)</script>` 的 `highlightedSource`，渲染后 DOM 无 script；npm run test 全过；grep 全仓命中列表 |
| T5 | `ecos_frontend/src/api.ts:34-51`（handleAuthExpired）+ `ecos_frontend/src/components/RequireAuth.tsx` + 全局 | T5a `handleAuthExpired` 扩展：403 → `notifyNoAccess()` 自定义事件 + toast，**不 removeItem token**（仅 401 做的事）；T5b `RequireAuth.tsx` 增加后端 `getMe()` 校验（参考 PMO-43 T4），403 → 跳 `#/no-access`（**新增路由** `App.tsx` 注册），不跳 `#/login`；T5c 创建 `src/pages/NoAccess.tsx`（≤50 行）"无权限访问"页（主题色 + i18n） | 浏览器：低权限账号访问 `/security-center` → `/no-access` 不跳 login；token 过期 → API 401 → 跳 login；`npm run lint` 0 错误 |

## §目标工时

5.5 小时（T1 0.7h + T2 1.0h + T3 1.2h + T4 0.8h + T5 1.0h + i18n 双份 keys 1.0h，含 Reviewer 缓冲）

## §依赖关系

- **前置**：PMO-43 T2 完成（toast provider 已挂载，T3 TaskPanel 改造依赖 useToast 可用）；**与 PMO-43 可并行开发**：T1/T2/T4 不依赖 PMO-43
- **输入**：检查报告 §3 / §5.1 项 7,9,10,11,13,14 / §4.4
- **后置**：PMO-46 QA 验收（4 主题 + 中英文切换）
- **Worker**: `fullstack-*`，**Verifier**: `reviewer-*`

## §分支

`feature/pmo-44-fe-p0-spec`

## §验收

- [ ] `npm run lint` 0 错误；`npm run build` 通过
- [ ] `grep -P  "[\x{4e00}-\x{9fa5}]" src/pages/Login.tsx` = 0（AIPKnowledgeView/TaskPanel/DataTable/NoAccess 同样 0）
- [ ] 浏览器 4 主题逐一：登录页 / AIPKnowledge / TaskPanel / DataTable / NoAccess 全部主题色正确
- [ ] cn/en 切换：上述页面全部文案正确
- [ ] XSS 单测通过（T4a script 标签无效）
- [ ] 后端 403 不跳 login（T5b 走 `/no-access`），401 跳 login
- [ ] Reviewer 代码审查 deliverable_allowed=true
