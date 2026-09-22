# Wave-5.4 T-18 · W-1 修复报告

> 日期: 2026-09-03 | 来源: Wave-5.3 T-16 Reviewer 2 warning 修复
> 范围: Wave-5.3 T-16 报 2 warning (W1 + W2) + Wave-4.1 v2 4 残留项 (W-1~W-4) 处置

---

## §W-1 修复（本日实际动）

### 问题
`ecos_backend/engine/kb-engine/kb-engine-impl/AGENTS.md` `## 端点 / 补丁` 节点 L37 写:
> `/api/v1/ecos/knowledge-graph` — Ecos 通用兼容端点（`EcosKnowledgeGraphController`）。

问题:
1. 路径张冠李戴 — 该 base 属于 gateway 侧同名 Controller（非 kb-engine-impl 侧）。
2. 仅单条是 gateway 副本路径，无 method 信息，无法判断 endpoint 数量。

### 实际源码勘定（双侧独立 base）

| Controller | 路径 | Method | 路由 |
|---|---|---|---|
| kb-engine-impl `EcosKnowledgeGraphController` | `/api/v1/knowledge/ecos-graph` | 1 GET (基线) | 取图快照 `ecosKgService.getGraphSnapshot()` |
| 同上 | `/api/v1/knowledge/ecos-graph/sync` | 1 POST | 同步到 Neo4j `ecosKgService.syncToNeo4j()` |
| gateway 副本 `EcosKnowledgeGraphController` | `/api/v1/ecos/knowledge-graph` | 1 GET (基线) | 取图快照 |
| 同上 | `/api/v1/ecos/knowledge-graph/sync` | 1 POST | 同步到 Neo4j |

> 注: 任务描述曾报 EcosKnowledgeGraphController "7 method"（/graph /graph-children /search /sync /sync-entities /entities /paths/entities），实际 grep 两侧均为 2 method。任务默示 7 endpoint 不符上游现状，按源码实况报 2 method 落盘；未加入子路径，避免引入全新虚构 endpoint。

### 改动
仅 1 文件 1 行（带上下文注释）`:ecos_backend/engine/kb-engine/kb-engine-impl/AGENTS.md` L37 改为:
- `/api/v1/knowledge/ecos-graph` — Ecos 通用兼容端点（`EcosKnowledgeGraphController`，2 method：`GET /` 取图快照 / `POST /sync` 同步到 Neo4j）。
- 注意：gateway 侧同名 Controller 用 base `/api/v1/ecos/knowledge-graph`，本模块侧路径独立，勿混。

### 验收
- `grep -n "knowledge" .../kb-engine-impl/AGENTS.md` — 输出 37 行带 method 名单
- 不再出现 kb-engine-impl 侧 base 错引 gateway 路径

---

## §W-2 不修（推 Wave-5.4 T-23）

CognitiveEngine2Application 主类 `cognitive2` 历史 repackage，GateWay Application `excludeFilters` 已重挂旧位置副本，不重名 1000+ 文件。`cognitive-engine-boot/AGENTS.md` §Strategy 已注 "cognitive2 包名 read only"。本日不修复，推 Wave-5.4 T-23 与 W-1 memory manifest 桥同步走 Amdp convention。

---

## §Wave-4.1 v2 4 残留项处置（本日全部推 Wave-5.5）

| 残留项 | 推 Wave-5.5 原因 | T-负责 |
|---|---|:--:|
| W-4（inline test 新 file 限制） | inline test 需后端 stats 对齐前端，归 Wave-5.5 | T-23 |
| W-1（memory manifest 桥） | kb-engine memory manifest 写周期不周知，归 Wave-5.5 | T-23 |
| W-2（forward / rollback / GB） | Amdp quality gate 已抽 wave，推 Wave-5.5 | T-23 |
| W-3（data field UI root cause） | Awrds T-21 已 fix 后端 stats，前端自有 UI 默认 fido 不动 | 推 Wave-5.5 |

本日 0 Wave-4.1 残留项改。

---

## §落地文件
- 改: `ecos_backend/engine/kb-engine/kb-engine-impl/AGENTS.md` (1 处 3 行)
- 新增: `docs/08-产品化重构方案/15-Wave5.4-T18-W1-fix.md` (本篇)

## §不在范围
- cognitive-engine-boot 包名 rename（IronLaw 5.1 #10 不破 baseline）
- gateway 副本 `EcosKnowledgeGraphController` base 也不动（与 kb-engine-impl 独立）
- 其他 Wave-5.3 12 AGENTS.md 不动

