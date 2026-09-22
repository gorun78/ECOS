# PMO-47 最终验收门禁（P0 15 项 + smoke + 4 主题 + zh/en）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: QA（reviewer 协助）
> 铁律:
> 1. P0 15 项全部 `deliverable_allowed=true` 才允许交付
> 2. 必须跑 `ecos-tests/data-workbench-smoke.mjs`（含监控心跳）
> 3. 4 主题（slate-light / deep-space / cyber-terminal / royal-purple）切换无视觉异常
> 4. zh/en 切换无遗漏（**用 useLanguage 全仓 grep + 浏览器手动**双验证）

报告来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §5.1（P0 15 项）+ §6 #7 + §7 验收自检清单（10 项）。

## §禁止清单

1. ❌ `deliverable_allowed=false` 时不允许交付（无例外）
2. ❌ 不允许 smoke 失败时"豁免通过率"继续使用
3. ❌ 不允许 Reviewer 缺席（Reviewer 必须是 `reviewer-*` profile）

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos-tests/data-workbench-smoke.mjs` 既有脚本重跑 | 执行 `node data-workbench-smoke.mjs`，要求全部场景（登录/Agent Builder/Agent Test/Data Workbench/Monitor）都通过；**额外补充**：新增 26 个 P0 端点的 curl 全验证一并加入 smoke（参考 PMO-42 T1 输出） | `node data-workbench-smoke.mjs` 退出码 0；smoke log 全 pass；含 26 个 P0 端点 curl 全 2xx |
| T2 | `ecos-tests/.results/p0-mark-done-47.md`（新文件）| 写本批次的 P0 15 项状态标记文档（含 `before fix → after fix` 模式），每项 ✅/❌ + 验证方式（截图/JSON/curl），引用对应 PMO 指令号 + 任务号 | 文档存在；15 项全 ✅；任一 ❌ → 整体 failed |
| T3 | 浏览器 E2E：4 主题 + zh/en 切换 | 用 Chrome DevTools MCP（或 Playwright）模拟：依次切换 4 主题 × 3 重点页（Login/AIPKnowledge/TaskPanel）× 2 语言（zh-CN / en-US），验证：① 无视觉差异（深色主题下 Login 不再白底白字）；② cn/en 切换后文案无遗漏（grep 中文 + 截图） | 截图存档到 `ecos-tests/.results/theme-i18n/`，路径：`<theme>-<lang>-<page>.png`；12 张截图；`grep` 中文字符在 Login/AIPKnowledge/TaskPanel/DataTable/NoAccess = 0 |
| T4 | smoke+主题+i18n 综合 review 自检 | 综合 T1+T2+T3 输出 `ecos-tests/.results/final-47-verification.md`：含 P0 15 项 + smoke 结果 + 4 主题 12 张截图清单 + zh/en 切换结果；`deliverable_allowed` 总结判断 | 文档 exist；`deliverable_allowed=true` 仅当 15/15 ✅ + smoke 全过 + 12 截图全生成 |
| T5 | Reviewer-CodeReview 终检 | 派发 `reviewer-*` profile 执行 `reviewer-code-review` Skill，对 PMO-38-46 全部 Source Patch 做一次快速 review（重点：Bean 冲突 / 3 滤波器一致性 / i18n namespace 泄漏 / 主题样式泄漏 / XSS 残留） | `REVIEW_REPORT` 落仓 `docs/reviews/2026-09-05-final-review.md`；`REVIEW_REPORT_APPROVAL_RECORD.deliverable_allowed=true`；若有 P0 发现 → unblock 整批 |

## §目标工时

2.5 小时（T1 0.5h + T2 0.5h + T3 1.0h + T4 0.3h + T5 0.2h 派发）

## §依赖关系

- **前置**：PMO-38/39/40/41/42/43/44/45/46 全部 `deliverable_allowed=true`（**硬依赖**）
- **输入**：报告 §5.1 全 15 项 + §6 #7 + §7 自检
- **后置**：交付（PM 指派 `pm-auto-commit` / `pm-auto-deploy` 真实 deployment）
- **Worker**: `qa-*` 主导，`reviewer-*` 终检，`pm-*` synthesizer

## §最终门禁（**严格**）

**项目可交付当且仅当**：
1. ✓ P0 15 项全部 `deliverable_allowed=true`（T2 验证）
2. ✓ `node data-workbench-smoke.mjs` 退出码 0（T1 验证）
3. ✓ 26 端点 curl 全 2xx（T1 验证，依赖 PMO-42 输出）
4. ✓ 4 主题 12 张截图全生成 + 双语言切换全过（T3 验证）
5. ✓ Reviewer `REVIEW_REPORT_APPROVAL_RECORD.deliverable_allowed=true`（T5 验证）
6. ✓ `npm run lint` 0 错误；`npm test` 全过；`mvn install -DskipTests` 0 ERROR
7. ✓ 14 处 `window.confirm` = 0；6 自建 fetch 文件 = 0；5 处 `.catch e { console.warn, return empty }` = 0；6 处 console.log toast = 0
8. ✓ 4 大文件已拆，超 800 行 = 0
9. ✓ `ExtractionReviewPanel.tsx` DOMPurify 单测过
10. ✓ `main.tsx` 已挂 `ToastProvider`

任一不通过 → `deliverable_allowed=false`，整批回退到失败 PMO 重发

## §分支

`release/pmo-47-final-gate`
