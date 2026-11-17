# 合规审计报告 - PMO-43 前端 P0 边界-基础设施-网络

- 审计任务 ID：t_2e02d7bf (Final gate decision and metadata update)
- Swarm root / blackboard：t_04a6ee19
- 被验交付：worker t_64e6e69e run 80（分支 `feature/pmo-43-fe-p0-infra-network`，working tree 未提交）
- 工作流模式：L2（PM → Arch → Fullstack → Reviewer，本项目以 Gateway/Final-gate 汇合替代）
- 审计时间：2026-09-07 (CST)
- 审计者：ecos-pmo (Commander 流程监管者)
- 报告类型：COMPLIANCE_REPORT

## 合规状态：FAIL（P0 count > 0）

## 门禁裁定
- **GATE = FAIL**（不得写 `gate=pass`）
- 依据：`deliverable_allowed=false` 且 ≥1 P0 → quality_gate_failure（workflow-auditor 汇合门禁规则「Reviewer 有 P0 缺陷 → 不通过，代码冻结」）
- 唯一通过项：lint（tsc --noEmit）= 0 —— 但 lint 0 不构成放行条件（仅语法/类型门，不覆盖逻辑与架构规则）

## 上游三查结果
| 检查 | 任务 | 结果 | 关键证据 |
|------|------|------|---------|
| Lint (tsc --noEmit) | t_bde9d204 | PASS | exit 0，0 错 0 警告（连续 2 次） |
| 代码验证 (PMN-43 T1-T5) | t_75b3faf3 | **FAIL** | `deliverable_allowed=false`；P0_GATE=FAIL（2 P0）；P1_GATE=PASS；ARCH/SECURITY=PASS |
| 架构合规 (前端开发规范) | t_a1dfedf8 | **FAIL** | 2 CRITICAL + 4 HIGH + 1 MEDIUM |

## 独立复核（Commander 自检，证据级）
直接读取 `/home/guorongxiao/ECOS/ecos_frontend/src/` 源文件逐项坐实：
- C-001 坐实：`components/NetworkErrorBanner.tsx:65` → `timerRef.current = setTimeout(() => setDown(false), 120_000);`（onDown 中 120s 墙钟自消失；spec T5 要求仅 online / ecos-network-up 清屏）
- C-002 坐实：`components/RequireAuth.tsx:31` `checkedRef.current = true;`（fetch settle 前）+ `:43` `.catch(() => setState("ok")); // network error — fail open`（AbortError 落入 catch → 永久 state="ok"；`:31` 使 checkedRef=true 后不再复检；403→no-access `:40`、401→login `:39` 双双被绕过 = 静默权限旁路）
  - 注：端点已由 PMO-44 跟进改为 `/api/v1/auth/me`（`/api/v1/me` 修正），但 C-002 竞态逻辑未变。

## 问题清单（门禁阻断项汇总，去重）
### P0（必须修复，代码冻结）
- **P0-G1（C1 / C-001）** `NetworkErrorBanner.tsx:65` — 120s 墙钟自动消失，服务仍 down 时横幅即隐。修复：删 120s timer；`down` 仅经 `onUp`/`onOnline` 清（`:67-80` 已正确）。附：文件 134 行超自声明 ≤80（trim 常量/接口）。
- **P0-G2（C2 / C-002）** `RequireAuth.tsx:28-43` — `checkedRef` 前置 + `AbortError→setState("ok")` 竞态 → 403/401 永久旁路 = 静默权限绕过。修复：`checkedRef=true` 移入 `.then` settle 后；`.catch` 区分 `AbortError`（保持 "loading"）与真实网络错误（才 fail-open）。
- **P0-G3（代码验证 P0）** `EngineMonitor`/`GuardrailsView` 缺 403→`percent-403` 心跳回退；`App.tsx:130-168` 健康轮询绕过 NetworkErrorBanner 通道（t_75b3faf3 P0_GATE 两项）。

### P1（应修，架构块）
- **H1** `MonitoringCenter.tsx:62,117` — 全局 `ecos-403` 无卡片标识 → N 卡并发时单卡 403 触发全卡 noPermission+toast。修复：event detail 加 `cardId`/按 `def.apiBase` 过滤；`removeEventListener` 移入 `finally`。
- **H2** `EngineMonitor.tsx:76,81,125,140,155` — 魔法字符串 `'登录已过期，请重新登录'` / `'NO_PERMISSION'`（控制流字符串协议 + i18n 硬规则违例）。修复：改用 `api.ts` 的 `NoAccessError`/`isNoAccessError(e)` + `t("common.noPermission")`。
- **H4** `aiworkbench/index.tsx:287` — 内联样式硬编码 `#555`（§三 违例）。修复：用主题 token。

### P2/跟踪（非阻断，移交或记录）
- **M1** `error:'MOCK'` 标签（types.ts + api.ts `MOCK_DATA_ASSETS_TAGGED`）无 UI 消费（grep src/pages|components = 0 render sites）→ 失败时静默返回假数据无信号。PM 需定 scope；若 in-scope 则渲染标签或改 fallback 抛 NetworkError。
- **H3** `aiworkbench/index.tsx:104,108,163` 硬编码中文（pre-existing，T3 文件内）。
- **H5** `EngineMonitor`/`MonitoringCenter`/`GuardrailsView` 三份重复 `authHeaders()`+`apiFetch`/`apiCall`（pre-existing，T4 加剧）。
- **M-001** `aiworkbench/index.tsx:6-9,49` 生产路径 import mock（pre-existing）。
- **S2** `types.ts` `error?: 'MOCK' | string` 收敛为 `string`（无约束）。

## 升级人工介入（compliance-monitor：failed_exceeded）
验证器 lane `t_6d63b733` 已 **FAILED 2 次**（run 84 / run 92 → blocked，block_loop_detected）→ 触 `FAILED >= 2 → P0 升级人工介入`。此为 rerun-loop 冻结：**在 worker t_64e6e69e 落地 C1/C2/H1/H2（+P0-G3）重做之前，不得再派 verifier 空转**；需 PM 显式决策后再重开验证。

## PM 待决事项
- **OOS-1**：`api.ts` 33 处残存 wrapper `.catch{console.warn + return {data:[],total:0}}`（fetchUsers/fetchRoles/...）。及 T1 的 `error:'MOCK'` 标签 scope —— 归属 PMO-43 重做还是移交 PMO-44/45？（不改变本 gate 判定，C1/C2/H1/H2 已足以 block）

## 结论
- 合规状态：**FAIL**
- 建议：**打回修复（rework）** + 代码冻结 + 升级人工
- 下一步：PM 派 worker t_64e6e69e 执行 P0/P1 最小修复集（见上）+ 回答 OOS-1；重做落地后再重开 t_6d63b733 重验。在修复落地前 **root t_04a6ee19 不得置 gate=pass**。

## 证据制品
- `/home/guorongxiao/ECOS/docs/PMO/PMO-43-swarm-verdict-2026-09-07-run92.md`（run 92 双独立 reviewer + 现场复核）
- `/home/guorongxiao/ECOS/docs/PMO/PMO-43-arch-compliance-2026-09-07.md`（架构合规 7 findings）
- `/home/guorongxiao/ECOS/docs/reviews/t_75b3faf3_review_report.md` / `t_75b3faf3_review_approval_record.json` / `t_75b3faf3_dispatch_plan.json`（PMN 代码验证）
- Commander 独立源码坐实：`/home/guorongxiao/ECOS/ecos_frontend/src/components/{NetworkErrorBanner,RequireAuth}.tsx`（行级 cite）
