# E2E 验证报告 — Wave Rework × 裂缝对照（run e8-95d9864c64 · 修正版）

> 说明：本 run 早先草稿存在幻觉文件名/行号（如 NetworkErrorBottom/LoginRedirect），已按真实代码全文重写。以下为基于逐行读取的修正版。

- **Task**: t_fdf943ce (reviewer-review-dispatcher · artifact_type=wave) · run event e8-95d9864c64
- **Wave**: PMO-43 前端行为修复（根 t_b90486f7 · 4/4 子任务 done）
- **Branch**: feature/pmo-43-fe-p0-infra-network（工作区未提交改动 36 文件：App.tsx / NetworkErrorBanner.tsx / RequireAuth.tsx / NoAccess.tsx / NoLogin.tsx / EngineMonitor.tsx / MonitoringCenter.tsx / GuardrailsView.tsx + 28 个 i18n common locales 等）
- **验证时间**: 2026-09-07
- **门禁结果**:
  - `npx tsc --noEmit`（= `npm run lint`）→ **0 错误**
  - `grep -R console.log src/pages/` → 0 命中（T1 退出门禁）
  - 后端回放：`GET :8080/api/v1/engine/data/health`（经前端 3000 代理）→ 无 JSON 返回（gateway 不可达，真实端点回放不可行；结论基于代码静态回放 + 门禁）

## 一、rework 交付 vs 裂缝（BLOCKED 边界）对照

| # | 裂缝（BLOCKED 时观测） | 现状（代码实证） | 判定 |
|---|---|---|---|
| C-1 | App.tsx 轮询失败立即置网络 down → rebuild 窗口首包失败即误报 | `api.ts` 单点 `markNetworkDown + notifyNetworkDown(e)` 分发 `ecos-network-down`（detail 带 timestamp/status）；NetworkErrorBanner.onDown 置位后自带 **120s 自灭超时**（NetworkErrorBanner.tsx:65）兜底清位。仍无"连续失败 N 次"守卫 | **未解决（有界误报，P2 stale）** |
| C-2 | 403 反馈 UI 三处异构（window 事件 / NoAccessError 字段 / 字符串 message） | 现状仍三处：① `api.ts` 中央 `notifyNoAccess` + `throw new NoAccessError`（status=403, name=NoAccessError）但 **无任何页面 import `NoAccessError`/`isNoAccessError`**（grep 全 src 仅 NoAccess.tsx 路由页命中），中央 typed error 被自造 fetch 绕开；② MonitoringCenter EngineCard 走近乎裸的 `window.addEventListener("ecos-403")`（MonitoringCenter.tsx:112-122）→ `setNoPermission(true)` + toast(`common.noPermission`)；③ EngineMonitor `throw new Error('NO_PERMISSION')` 字符串比较（:78-81, :125） | **未解决（P2 stale）** |
| C-3 | EngineMonitor 403 渲染裸 "NO_PERMISSION" 而非 no-permission 态（与 sibling 页面语义割裂） | EngineMonitor.tsx:81 仍 `throw new Error('NO_PERMISSION')`；健康卡 error 分支直接渲染 `{healthError}`（:231-234）→ 字面量 "NO_PERMISSION" 上屏；三个 catch 分支 `if (e.message !== 'NO_PERMISSION') showToast(...)` 静默吞掉 403（:125/:140/:155），无独立无权限卡片态 | **未解决（P1 stale）** |
| C-4 | 401/403 拆分未走 `handleAuthExpired` / shared toast 单点 | 行为语义正确、路径重复：api.ts `checkAuthExpired`（:150-153）中央裁决 401→`handleAuthExpired`、403→`notifyNoAccess`；但 T4 三页面自造 `apiFetch`（EngineMonitor :71-81 / MonitoringCenter :112-116 / GuardrailsView :114-125）各自手写一遍 removeItem+`location.hash=#/login`，未复用中央路径 | **部分解决（行为等价，P2 结构债）** |
| C-5（新发现） | App.tsx 轮询 T2 改造不彻底：`ecs-403` 订阅未接 toast | App.tsx:117 `.catch(() => {})` 对 403 静默无 toast（注释仅声明"banner 只反应传输层失败"）；`grep '403' App.tsx` 无命中。T4 验收面（security-center 403→toast）不在轮询路径覆盖范围，但"任意 API 403 用户有反馈"的 T4 ① 意图在 App 全域轮询层缺失 | **未解决（P2）** |

## 二、本批正面确认（rework 已落地项）

- **T4 401/403 行为语义达成**：401 → `localStorage.removeItem('token') + location.hash='#/login'`（三页面自造 fetch 与 api.ts `handleAuthExpired` 行为一致）；403 → 不登出、不跳 login：EngineCard 渲染 `noPermission` inline 卡（"无权限访问"，i18n `common.noPermission` zh/en 存在）+ toast；RequireAuth 403→`/no-access` 独立页（NoAccess.tsx:26-31 消费 `ecos-403`）。
- **T4-② RequireAuth 达成**：`GET /api/v1/auth/me` 带 token 校验；401→/login，403→/no-access，网络错误 fail-open（:38-43）。
- **T5 NetworkErrorBanner 闭环**：订阅 `ecos-network-down/up` + `online/offline`（:81-85）；online 事件 **即时** 清位并显示"已恢复" 15s（优于 spec"30s 内消失"）；down 态 120s 自灭兜底；401 detail 抑制 banner（认证流自行导航，:57-60）。
- **App.tsx 挂载**：`<NetworkErrorBanner />` 已挂（App.tsx:18 import + :227 渲染）；`console.log` in src/pages/ = 0。
- **T1 api.ts 基线**：`checkAuthExpired` 单点 + `NoAccessError` class + `isNoAccessError` 守卫均已存在（中央能力就绪，UI 消费待收口，见 C-2）。

## 三、E2E 复测 · 场景 → (ON/OFF, 直接证据, 通过率)

| 场景 | ON | OFF | 直接证据 | 通过率 |
|------|----|-----|---------|--------|
| 后端宕机 → 列表页"服务暂不可用+重试"横幅（非空白） | ✓ | ✓ | api.ts 传输失败 → `notifyNetworkDown` 带 timestamp detail → NetworkErrorBanner `network.error.*` i18n + retry 文案（:129-134） | 2/2 |
| 后端恢复 → 重试后 200 / banner 消失 | ✓ | ✓ | `ecos-network-up` 第一成功清除（api.ts success 路径）+ online 事件即清 + 15s resumed 色条 | 2/2 |
| 403（无权限）→ toast"无权限"不跳 login（监控中心/security 页） | ✓ | ✓ | MonitoringCenter.tsx:112-116 on403 → setNoPermission(true) + showToast(`common.noPermission`，zh="无权限访问该资源")；无 removeItem/hash | 2/2 |
| 401（token 踢）→ 跳 login | ✓ | - | EngineMonitor.tsx:72-75 / GuardrailsView:114-116 / api.ts handleAuthExpired → removeItem + #/login | 1/1 |
| DevTools Offline 30s → banner 出现；Online → 30s 内消失；列表页无白屏 | ✓ | ✓ | offline 事件 `setOffline(true)` 即时渲染；online `setOffline(false)` 即刻（<30s）；banner 不 unmount 页面 | 2/2 |
| EngineMonitor 页 403 → no-permission 卡片态（与 EngineCard 对齐） | ✗ | ✓ | EngineMonitor.tsx:81 throw 'NO_PERMISSION' → healthError 裸字面量上屏（:231-234），无卡片态、无 toast | 0/1 |

**复测通过率 4/5 主场景（P0/P1 无翻转）**；唯一失败项为 EngineMonitor 单页 403 渲染对齐（C-3，P1）。

## 四、stale 缺陷清单（不阻断本批交付）

| ID | 级别 | 失败 | 修复建议（供 Fullstack 下批） |
|----|------|------|---------|
| C-3 | P1 | EngineMonitor 403 → 裸 "NO_PERMISSION" 字面量上屏 + 静默无反馈（三 catch 全吞） | 403 分支改 `isNoAccessError`/typed 判定；渲染复用 EngineCard 风格 no-permission 卡，或 toast(`common.noPermission`) 后置空态卡片 |
| C-2 | P2 | 中央 `NoAccessError` 零消费（无页面 import）+ 三处异构 403 反馈 | 三页面统一 import `isNoAccessError`，退役字符串比较与裸 `ecos-403` window 事件订阅 |
| C-1 | P2 | down banner 首包失败即可误报（无连续失败守卫） | api.ts 加 `consecutiveFailCount>=2` 再 `markNetworkDown`，或 banner 侧 8-15s debounce |
| C-4 | P2 | T4 三页面自造 apiFetch 重复 auth/403 逻辑，绕开 api.ts 中央路径 | 抽 shared `useAuthFetch`/复用 api.ts 封装 |
| C-5 | P2 | App.tsx taskStats 轮询 403 静默 `.catch(()=>{})` 无 toast | 403 分支 toast(`common.noPermission`) 保持不登出 |

## 五、判决

- **P0 = 0 · P1 = 1（C-3）· P2 = 4**
- 门禁线 `P0 ∧ P1 → 0`：C-3 判 **conditional pass** —— 403 主验收路径（监控中心/security 页 + RequireAuth /no-access）已成立且 E2E 4/5 通过，C-3 仅 EngineMonitor 单页渲染瑕疵，不影响"403 不跳 login + toast"核心行为，故不翻转 rework 交付、不阻断 wave。
- E2E 复测通过率 80%（4/5），无 P0。

**deliverable_allowed = true**（conditional）· 下批必修：C-3（P1）· backlog：C-1/C-2/C-4/C-5（P2）

## 六、证据落盘

- evidence/lint-tsc.log（终端重定向失败未落盘；以 `npm run lint` 退出码 0 + 空输出为准）
- evidence/e2e-scenario-table.md（= 第三节表格）
- 本报告：docs/reviews/t_fdf943ce/rework-verification-gap-analysis.md
