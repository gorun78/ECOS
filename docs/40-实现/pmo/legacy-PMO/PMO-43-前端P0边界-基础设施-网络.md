# PMO-43 前端 P0 边界/基础设施/网络（5 文件 + 8 文件）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md) §4 前端铁律
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: fullstack-implementer (FE)
> 铁律:
> 1. 公共组件 → 抽离公共层（`src/components/common/`），禁止 30+ 页各写一份 `<Toast>` 副本（§4.x/前端规范五）
> 2. API 层禁止把 5xx/TypeError 映射成"空数据"（前端规范一：明确兜底；不安全反模式）
> 3. 后端宕机识别：`fetch` reject（`TypeError`）= 网络故障，须展示"服务暂不可用 + 重试"，**不得静默**

报告来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §5.1 P0 项 2-4, 5-6, 8, 14。本批共 5 个 P0 项 → 拆 5 个 Task（含 T5 网络层）。

## §禁止清单

1. ❌ `ToastProvider` 挂上后仍保留 30+ 页重复 `<Toast>` 实现 — **本任务只挂 Provider**，30+ 页副本由 PMO-45 后续统一删除
2. ❌ 不引入新 npm 依赖（除 `dompurify` 是已有的或可在 PMO-45 补；本批不涉及）
3. ❌ 不新建模块化目录（严守 src 标准目录结构）
4. ❌ 不引入 store 新全局状态（Pinia 风格 if any）
5. ❌ 改 `ErrorBoundary` 不加 useTheme（语义色）

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos_frontend/src/api.ts` | ① 行 144-188、294-322、1254-1255、728-770、2318-2325 5 处 `.catch(e => { console.warn(...); return { data: [], total: 0 }; })` 反模式 → 改为 `.catch(e => { notifyNetworkDown(e); return { data: [], total: 0, error: normalizeError(e) }; })`，新增 `notifyNetworkDown` 单点声明（在 api.ts 顶部）：抛 typed `NetworkDownEvent` 给 EventBus 挂载到 `window.dispatchEvent(new CustomEvent('ecos-network-down', ...))`；② 行 42-97 三个 fetch 封装加 `TypeError` 识别，**返回 reject 而非 silent**；③ 新增 `NetworkError extends Error`，凡 fetch reject 都 `throw new NetworkError()`；④ `fetchDatasets` 行 150 `return MOCK_DATA_ASSETS` 改为 `return realData || { data: [], total: 0, error: 'MOCK' }` 标注 MOCK（区分真实 vs 兜底） | `npm run lint` 0 口径；grep 命中 0 `.catch(e => { console.warn` + `MOCK_DATA_ASSETS`；模拟后端宕机（`lsof -ti:8080 | xargs kill -9`）→ 浏览器打开任意列表页显示"服务暂不可用 + 重试"（不是空白）；再 `bash ~/start-gateway.sh` 启动后点重试按钮恢复 200 |
| T2 | `ecos_frontend/src/main.tsx` + `ecos_frontend/src/App.tsx` | 在 `main.tsx` 挂 `<ToastProvider>`（来自 `src/components/common/Toast.tsx`），包裹 `<App>`，删除 8 处 `showToast={(type,msg) => console.log(...)}` 残缺 handler（main:86、AIPKnowledgeView:25、WorkshopView:27、ScenarioManagementView:28、DataWorkbenchLayout:49）；`App.tsx:117` 的 `setInterval(poll, 10000)` 增加 `navigator.onLine` 持有、`taskStats` 失败时把网络横幅显示（通过 EventBus 订阅 `ecos-network-down`） | `npm run lint` 0 错误；`grep -R "console.log" src/pages/` 命中 0；任意 toast 在 3 个页面（TokenManager/AIPKnowledge/Workshop）可见；后端宕机时顶栏出现红色"已断开"横幅 |
| T3 | `ecos_frontend/src/pages/aiworkbench/index.tsx` + `ecos_frontend/src/pages/AgentMesh.tsx` | ① `index.tsx:44-47` 4 个 `fetchAIPAgentsFromMesh().then(setAgents).catch(() => {})` 静默吞错 → 改为 `.catch(err => { setError(err); toast.error(err.message); })`，加 `useErrorState` 局部 state 渲染 `<ErrorBanner>` 含重试按钮；② `AgentMesh.tsx:66-68` 同理：`agents`/`missions` 加 loading/error/empty 三态 + 重试 | 浏览器手动：触发后端宕机 → aiworkbench Tab 底部 `<ErrorBanner>` 出现 + "重试"按钮；AgentMesh 列表无白屏；后端恢复后重试 → 3 个 Tab 加载 agents/guardrails/pipelines/models 列表 |
| T4 | `ecos_frontend/src/pages/EngineMonitor.tsx` + `src/pages/MonitoringCenter.tsx` + `src/pages/GuardrailsView.tsx` | ① `EngineMonitor.tsx:71-73` / `MonitoringCenter.tsx:51-53` / `GuardrailsView.tsx:114-116` 当前对 **401 和 403 都 removeItem 跳 `#/login`** → 拆为：401 走 `handleAuthExpired`（跳 login），403 → `toast.error(t("common.noPermission"))` 不跳；② `RequireAuth.tsx` 只判 `localStorage.token` 的问题 → 改为 `getMe()` 调一次后端（带 token），401/403 都跳 login 之外的"no access"页 | `npm run lint` 0 错误；浏览器登录低权限账号访问 `/security-center` → 403 → toast "无权限" 不跳 login；踢 token → 任意 API 401 → 跳 login |
| T5 | `ecos_frontend/src/components/NetworkErrorBanner.tsx`（新文件）+ `ecos_frontend/src/api.ts:42-97` + `ecos_frontend/src/App.tsx` | T5a 新增 `NetworkErrorBanner` 公共组件（≤80 行）：订阅 `window.dispatchEvent('ecos-network-down')`，渲染固定顶部红色横幅 "网络已断开"（主题色 + i18n）；T5b `api.ts` 的 request 加 `online/offline` 事件订阅，offline 时拦截 fetch → 不发请求，UI 出现"已断开"；T5c `App.tsx` 顶部挂载 `<NetworkErrorBanner />` | 浏览器 DevTools Offline → 任意列表页：横幅出现 + 不发新 fetch；恢复 Online 30s 内横幅消失；所有列表页无白屏 |

## §目标工时

5 小时（T1 api.ts 改造 1.2h + T2 main/App 1.0h + T3 两页面 0.8h + T4 三页面 1.0h + T5 三文件 1.0h）

## §依赖关系

- **前置**：PMO-43 不依赖其他 PMO（可独立开发，但端到端验证需 PMO-38-42 后端端点或部分端点可用）
- **输入**：检查报告 §5.1 项 2,3,4,5,6,8,14 + §3.6（6 处 console.log toast）+ §4.5（断网 UI）
- **后置**：PMO-45（XSS/i18n/14 处 confirm）、PMO-46（QA 验收 项 2/4/7 都依赖本批）
- **Worker**: `fullstack-*`，**Verifier**: `reviewer-*`

## §分支

`feature/pmo-43-fe-p0-infra-network`

## §验收

- [ ] `npm run lint` 0 错误
- [ ] `npm run build` 通过
- [ ] 后端宕机 → 列表页"服务暂不可用 + 重试"显示；恢复 → 重试 200
- [ ] 后端 403（无权限）不跳 login，正确 toast
- [ ] 后端 401 → 跳 login（保留行为）
- [ ] DevTools Offline 30s → 横幅出现；Online → 30s 内消失；所有列表页无白屏
- [ ] `grep -R "console.log" src/pages/` = 0；`grep -R "return { data: \[\]" src/api.ts` 仅允许 `error: 'MOCK'` 标注命中
- [ ] Reviewer 代码审查 deliverable_allowed=true
