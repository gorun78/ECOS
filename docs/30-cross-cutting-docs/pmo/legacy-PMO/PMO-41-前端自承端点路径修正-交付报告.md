# PMO-41 前端自承端点路径修正 — 聚合交付 (Synthesis)

日期: 2026-09-06 · 批次: PMO-41 · 任务: t_d3cc5e11 (synthesizer)
Swarm: root t_8fe72be2 / worker t_448ed2bb / verifier t_6c8f7913 (gate PASS) / synthesizer t_d3cc5e11

## 结论

Gate 已通过（verifier 判定 PASS），PMO-41 本批次 4 个文件的端点路径修正已落地并经后端源码
级核实，聚合完成。仓库当前工作区为多批次并行（PMO-41/43/44 + w10 P2），本批次 4 文件
与同槽 WIP 零 entangle（已 grep 交叉确认），故未对同槽并行批次做破坏性 stash/checkout
重置；git 提交由 PMO 主流程统一处理。

## 交付内容（4 files, +51 −17）

| # | 文件 | 修正 |
|---|------|------|
| T1 | `ecos_frontend/src/services/ontologyApi.ts` | `fetchVersions` → `GET /api/v1/ecos/versions?domainCode=`（OntologyVersionSimpleController @RequestMapping("/api/v1/ecos/versions")）；`fetchVersionDiff` 真后端无 `/versions/{id}/diff` controller，本批次按 verifier 判定保持不变并留注记，待批次 3 真 endpoint |
| T2 | `ecos_frontend/src/services/agentConfig.ts` | 去掉自建 `authHeaders`，统一改用 `../api` 的 `apiFetch` 复用集中式 auth/proxy |
| T3 | `ecos_frontend/src/features/.../sql-query-console/api.ts` (`ecos_frontend/src/pages/sql-query-console/api.ts`) | `fetchTemplates`/`deleteTemplate` 改复数 `/api/v1/engine/data/query/templates`（QueryController）；`saveTemplate` 保持单数 `POST /template`（后端 Task 15 POST /template 自承） |
| T4 | `ecos_frontend/src/pages/aiworkbench/api.ts` | `fetchAgentMetrics`/`fetchAgentErrors` 由前端自承别名 `/api/v1/agent-metrics/...` 改主路径 `/api/v1/aip/agent-metrics/{id}` + `/{id}/errors`（AgentMetricsController），字段对齐 totalCount/avgElapsedMs/p99Ms、errors {total,errors} |
| T5 | 全仓 grep 闸 + lint | grep 代码级真错路径 0；本批次 4 文件 lint 0 |

## 验证证据（独立复验）

- `grep VERSION_BASE` → `/api/v1/ecos/versions`（ontologyApi.ts:768）✓
- `grep apiFetch / authHeaders` → agentConfig.ts 无 authHeaders，`import { apiFetch } from "../api"` ✓
- `grep templates` → sql-query-console/api.ts:24 复数路径 ✓
- `grep agent-metrics` → aiworkbench/api.ts:383,413 主路径 `/api/v1/aip/agent-metrics` ✓
- `npm run lint`（tsc --noEmit）: 本批次 4 文件 0 错误；仓库级唯一 lint 错 `src/api.ts(59,78)` TS2352 为 PMO-43 外来 WIP，已报告、未动（非本批次范围）✓

## 机器可读事实（completion metadata 镜像）

```json
{
  "batch": "PMO-41",
  "diff_stat": "4 files changed, 51 insertions(+), 17 deletions(-)",
  "files": [
    "ecos_frontend/src/services/ontologyApi.ts",
    "ecos_frontend/src/services/agentConfig.ts",
    "ecos_frontend/src/pages/sql-query-console/api.ts",
    "ecos_frontend/src/pages/aiworkbench/api.ts"
  ],
  "gate": "pass",
  "gates": {
    "build": "PASS",
    "grep_code_truly_wrong": 0,
    "lint_batch_4_files": 0,
    "lint_repo_wide": 1
  },
  "lint_repo_wide_note": "仅 src/api.ts:59 (PMO-43 外来 WIP, TS2352)，非本批次；已报告未改",
  "worker_session_id": "20260906_192024_bb9c0f",
  "foreign_wip": "ecos_frontend/src/api.ts 含 PMO-43 WIP，未并入本次交付"
}
```

## 遗留 / 交下一步

1. `fetchVersionDiff` 真 endpoint 待 PMO-39/40/41 批次 3 后端版本 diff controller 落地后回接（本批次按保持）。
2. 仓库级 `src/api.ts(59)` lint 错归 PMO-43 批次，另行处理。
3. git 提交落在 PMO-41/43/44 同槽 WIP 之上，需 PMO 主流程在批次全部收敛后统一 commit。
