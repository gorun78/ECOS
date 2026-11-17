# REVIEW_REPORT — T5 NetworkErrorBanner（t_40156e23）

- 任务：Verify T5: NetworkErrorBanner component
- 模式：L1 快速代码审查（reviewer-code-review quick mode）
- 审查对象：`ecos_frontend` 未提交工作树（分支 `feature/pmo-43-fe-p0-infra-network`）
- 审查范围：`src/components/NetworkErrorBanner.tsx`（新文件，134 行，未 git add）+ `App.tsx` 挂载点 + `api.ts` 事件源
- 报告时间：2026-09-07 08:37 CST
- deliverable_allowed: **false**（见 §5 质量门禁）

---

## 1. 验收项逐条核对（任务正文 Acceptance）

| # | 验收项 | 判定 | 证据 |
|---|--------|------|------|
| A1 | 使用 `navigator.onLine` 显示离线横幅 | ✅ 通过 | `NetworkErrorBanner.tsx:31-33` 初始状态 `!navigator.onLine`；`:81-86` 订阅 `offline`/`online` 事件 |
| A2 | Banner 随网络状态出现 | ⚠️ 未证实（直播验证未执行，见 §4 说明）；静态逻辑成立 | `onOffline` (:81) → `setOffline(true)` → `visible` (:96) → render danger banner (:117-133)；事件链完整 |
| A3 | Banner 随网络恢复消失 | ⚠️ 部分成立 | `onOnline` (:74-80) → `setOffline(false)` ✅；但「服务宕机」通道被 120s 自毁破坏，见 C-001 |
| A4 | 集成到 layout | ✅ 通过 | `App.tsx:18` import、`App.tsx:227` `<NetworkErrorBanner />` 挂载在布局根（`h-screen` 容器内第一个子节点），fixed top-0 z-[100] 顶栏横幅 |

## 2. 规格符合性（PMO-43 T5a，`docs/PMO/PMO-43-前端P0边界-基础设施-网络.md` §25）

| 规格项 | 判定 | 证据 |
|--------|------|------|
| 新文件 `src/components/NetworkErrorBanner.tsx` | ✅ | 存在，untracked |
| 订阅 `ecos-network-down` | ✅ | `:83` addEventListener |
| 固定顶部横幅、主题色 + i18n | ✅ | `:121` theme tokens `styles.dangerBg/Text/Border`；文案全部 `t("network.*")`（zh-CN/en locales :46-51） |
| **组件 ≤80 行** | ❌ FAIL | 实际 134 行（`wc -l` 实测） |
| offline 时拦截 fetch 不发请求（T5b api.ts） | ✅（附带） | `api.ts:44-48` wrapNet 判定含 `navigator.onLine === false` |

## 3. 缺陷清单

### C-001 ｜ P0 ｜ bug/critical
- 位置：`ecos_frontend/src/components/NetworkErrorBanner.tsx:61-65`
- 描述：`onDown` handler 无条件 `setTimeout(() => setDown(false), 120_000)` —— `ecos-network-down` 后 120s（墙钟）自动隐藏「服务不可用」横幅，即使后端仍未恢复。且 `notifyNetworkUp`（`api.ts:98-111`）仅在「down 后 5 分钟内拿到 fetch 成功」才发 `ecos-network-up`，若用户停留在不发请求的页面，横幅既不会由 onUp 清、120s 后反而擅自消失，恢复时用户已失去告警感知。
- 影响：核心验收点 A3 的反面 —— 横幅在「服务仍宕机」时消失，违背该组件 docstring（"disappears automatically when online fires or when a fetch succeeds again"）与规格「横幅只在 Online/恢复时消失」的语义。
- 修复建议：删除 `:61-65` 的定时器路径；`down` 仅由 `onUp`/`onOnline` 复位。组件可顺势瘦身 ≤80 行。

### P1（非阻断，记录在案）
- **P1-1** `NetworkErrorBanner.tsx:55-60` — 组件内硬编码 `detail?.status === 401` 魔法值抑制横幅。T1 已在 `api.ts` 定义 `NetworkError`/`NoAccessError` 契约，此 magic-number 判断应替换为 `detail?.kind === 'auth'` 之类的显式契约字段，避免与 api.ts 语义漂移。
- **P1-2** `NetworkErrorBanner.tsx:12` 注释声明 "Keep ≤80 lines"，实际 134 行 —— 注释与代码失配（C-001 修复后若仍超标则注释失效）。

### 设计债（追溯父卡 3 次 verifier 结论，非本卡引入，列出供 Final Gate 参考）
- `App.tsx:~137` health-poll `apiHealth().catch(() => setServiceStatus("DOWN"))` 未走 `notifyNetworkDown()` 通道（App.tsx:130 注释意图如此但代码未落地）→ 后端宕机但浏览器 Online 时，Sidebar 芯片变 DOWN 而横幅不出现，与「后端宕机时顶栏出现红色横幅」的 T2 验收存在残余缺口（C-002，P1）。

## 4. 动态验证说明（诚实声明，杜绝假绿）

本卡执行了真实浏览器会话（headless Chrome @ http://localhost:3000，Vite dev server pid 87653 已确认伺服含 NetworkErrorBanner 的 App.tsx，hash 路由未认证重定向到 `#/login`）。`offline` 事件注入测试在已复核的 App 挂载状态下未复现横幅显示，因未登录态页面（Login）不挂载 `App` 布局壳，NetworkErrorBanner 不在 DOM —— 该路径正确（Login 页无横幅是预期行为）。**受限于无可用登录凭据，A2「横幅实际出现在已布局页面」一项未能拿到 DOM 级正向证据**；判定基于完整事件链静态推演（`offline` → setOffline(true) → visible → render），代码路径无歧义。不为规避缺证而标注「已通过实测」。

## 5. 质量门禁判定

| 门禁 | 状态 | 依据 |
|------|------|------|
| P0_GATE | ❌ FAIL | C-001（1 个 P0） |
| P1_GATE | ✅ PASS | 本卡 scope 内 P1 ≤ 3（P1-1/P1-2；C-002 归属 App.tsx 通道，父卡已立卷） |
| SECURITY_GATE | ✅ PASS | 无 CRITICAL/HIGH；无注入面（纯展示组件，无 dangerouslySetInnerHTML） |
| ARCH_GATE | ⏸ 移交 | L1 quick 模式不含 arch-consistency 并行子审查；同 scope 架构一致性风险（api.ts 契约 vs 组件 magic number）已由 P1-1 捕捉，全量 L3 由父卡 t_6d63b733 的并行子卡承担 |

**deliverable_allowed = false** —— 阻断原因：1 个 P0（C-001）。

> 备注：该 P0 与父卡 `t_6d63b733` run 84/92/103 三次 verifier 的 C-001 结论一致（同一 `:65` 行，同一修复方案），本卡为独立复核，非照抄。

## 6. 改进建议（不阻断）

1. C-001 修复后，组件行数预计降至 ~115 行以内；如需 ≤80 可把 `resumed` 成功提示块抽取为 `SuccessFlash` 小子组件（建议，非红线）。
2. `AUTO_DISMISS_MS = 15000`（:26）与 api.ts `notifyNetworkUp` 的 5 分钟窗口是两套独立计时语义，建议统一命名并加注释说明各自语义，避免后续维护者混淆。
3. `prop Drilling`：无（组件自订阅 window 事件，props=0，符合公共组件定位）。

## 7. 缺陷 → PRD 追溯表

| 缺陷 ID | 描述 | 优先级 | PRD 来源 | 文件位置 |
|---------|------|--------|---------|---------|
| C-001 | 120s 自毁破坏「服务宕机横幅持续显示」语义 | P0 | PMO-43 §25 T5a（横幅生命周期） | NetworkErrorBanner.tsx:61-65 |
| P1-1 | 401 魔法值判断 | P1 | PMO-43 §25 T1（NetworkError 契约） | NetworkErrorBanner.tsx:55-60 |
| P1-2 | ≤80 行注释与实现失配 | P1 | PMO-43 §25 T5a（≤80 行） | NetworkErrorBanner.tsx:12 |
