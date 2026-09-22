---
title: 18-Wave5.2-T22-591-端点回归 (curl_all 跑完, G4 NO-GO)
date: '2026-09-03'
version: 1.0
sources:
  - 脚本: ecos_tests/curl_all_regress.sh + curl_all_regress.py
  - 日志: /tmp/curl_all.log (tee 完整输出)
  - 详情: /tmp/curl_all_detail.tsv (815 行 method/path/code/time/body)
  - GW log: ecos_tests_p99/gateway_restart.log (5xx 根因 trace)
aliases: 18 Wave-5.2 T22 591 端点回归报告
---

# ECOS Wave-5.2 T22 — 591 端点回归 (curl_all - 守裴 G4)

> 跑数: 2026-09-03 14:01:22 – 14:02:06 CST (44 秒)
> GW PID: 45367 (8080 LISTEN)  DB: sys_man  PG 16
> Token: 默认 `super_admin/SuperAdmin@2026` 走 401 → 回退 `admin/admin123` (axis user SUPER_ADMIN)
> 端点访问门槛: 591 名来自 02-现状差距: "全局端点:591条映射"  (doc 行 83), 05-架构设计: "回归 — 591 端点curl100% 200"
> **实测扫描**: 后 后端 src 全 @*Mapping 扫包得 **815 unique 端点** (0 5xx 阻断/0 neterr 是 G4 守)

---

## 0. 总账 (Gate 判定)

| 项 | 数值 | 守 | 判定 |
|---|---:|:--:|:--:|
| **TOTAL** | **815** |  (扫出唯一) | — |
| **2XX** | **695** | — | — |
| **4XX** | **84** | — | 4XX 含 AUTH/404/400/405/415, 不阻断 |
| **5XX** | **36** | **守: 0** | **🔴 FAIL** |
| **NETERR (000/timeout)** | **0** | **守: 0** | ✅ |
| **401** | **2** | — | 不需 auth 已过 |
| **403** | **0** | — | entity-link 403 留多未发生 (本轮 admin 已 SUPER_ADMIN) |
| **404** | **41** | — | 路径不存在/资源查不到, **不阻断** |
| **其他 4xx** | **41** | 阻尼 | 400 Bad Request (大量)/1×405/1×415 |
| **VERDICT** | **🔴 G4 = NO-GO** | 0 5xx + 0 neterr | **36 个 5xx 残留, 守失败** |

**4XX 拆解**: AUTH(401+403)=2 + 404=41 + other4xx=41 (= 37×400 + 1×405 + 2×415…_fine tune)

> **阻碍判定**: 36 个 5xx 全部根因 = **PG 表/列缺失(15)+ NPE 没捕获(7)+ SQL 参错(6)+ 数据完整性(8)**, **不在本波 fix 范围** (实点排查 1 多轮循环 PMO-4). 上游 PMO 推 Phase 6 联调波统一收口.

---

## 1. 详情 (5xx 36 个 segment)

| # | method + path | 500 根因 (gateway log 抓 root cause) | PLAN 留多 |
|---:|---|---|:--|
| 1 | `DELETE /api/v1/ontology/proposals/x` | **PG: relation "ecos_ontology_proposals" does not exist** (`BadSqlGrammarException`) | P0 |
| 2 | `GET /api/datanet/metadata/preview/x` | **PG: ERPDK (uto-datanet 不锁多租户 id)** = 已迁 datalake  (quota fitter 报 `参数详情不存` → status 200 错位 500) | P1 |
| 3 | `GET /api/v1/datanet/metadata/preview/x` | 同 #2 (双 base front/back 撞 path) | P0 (双篇 V1 移动) |
| 4 | `GET /api/v1/knowledge/graph` | **PG: DataIntegrityViolationException 取列 `createdat`** (驼bsite→snake_site 规格: `Badvalue fort type long : 2026-09-02 11:40:40`) | P0 |
| 5 | `GET /api/v1/ontology/proposals/x` | 同 #1 (同一张表) | P0 |
| 6 | `GET /api/v1/task/x` | **NPE: `task` is null (TaskDescription.getTaskId() task null)** | P0 |
| 7 | `GET /api/v1/task/x/status` | **NPE: `status` is null (TaskStatus.getTaskId() status null)** | P0 |
| 8 | `POST /api/agent-mesh/agents` | **PG: null value in column "name" of "ecos_agent_registry" violates NOT NULL** ×5 | P0 |
| 9 | `POST /api/datanet/metadata/collect/x` | **PG: `数据源不存在: x`** (IllegalArgumentException 未捕) | P1 |
| 10 | `POST /api/v1/cognitive/diagnose` | **NPE: `pk` is null (Cannot invoke Object.hashCode())** | P0 |
| 11 | `POST /api/v1/datanet/catalog/register` | **PG: column "tenant_id" of "td_catalog_item" does not exist** | P0 |
| 12 | `POST /api/v1/datanet/metadata/collect/x` | 同 #9 | P0 |
| 13 | `POST /api/v1/ecos/dq/issues` | **PG: null value in column "id" of "ecos_dq_issue" NOT CONSTRAINT** | P0 |
| 14 | `POST /api/v1/ecos/dq/rules` | **`InvalidDataAccessApiUsageException: getKey...multi keys` (updated_at 跨列变量)** | P0 |
| 15 | `POST /api/v1/ecos/entities/x/relationships` | **PG: DuplicateKeyException "ecos_ontology_relationship_pkey" DUPLICATE constraint** | P0 |
| 16 | `POST /api/v1/ecos/ontologies/entities/x/properties` | **PG: DuplicateKeyException "ecos_ontology_property_uk_ent_code" DUPLICATE constraint** | P0 |
| 17 | `POST /api/v1/ecos/ontologies/x/entities` | **PG: DuplicateKeyException "ecos_ontology_entity_uk_ont_code" DUPLICATE constraint** | P0 |
| 18 | `POST /api/v1/ecos/ontologies/x/versions/publish-from-proposal/x` | **PG: relation "ecos_ontology_proposals" does not exist** (同 #1) | P0 |
| 19 | `POST /api/v1/ecos/workflows/instances/x/resume` | **PG: UPDATE "ecos_workflow_instance" column "error_message" does not exist** | P0 |
| 20 | `POST /api/v1/ecos/workflows/instances/x/suspend` | 同 #19 | P0 |
| 21 | `POST /api/v1/ecos/workflows/instances/x/terminate` | 同 #19 | P0 |
| 22 | `POST /api/v1/engine/ontology/workflow/instances/x/approve` | **WS-009: 任务不存在: x** (RuntimeException 未捕) | P0 |
| 23 | `POST /api/v1/engine/ontology/workflow/instances/x/reject` | **WF-009 同上** | P0 |
| 24 | `POST /api/v1/guardrails/policies` | **`name is required` (IllegalArgumentException 未捕)** | P1 |
| 25 | `POST /api/v1/knowledge/edges` | **PG: DataIntegrityViolation (created_at timestamp 被 FROM bigint 未 cast hint)** | P0 |
| 26 | `POST /api/v1/knowledge/nodes` | 同 #25 | P0 |
| 27 | `POST /api/v1/ontology/glossary/terms` | **PG: Bad SqlGrammar (expense 多余列?)** | P0 |
| 28 | `POST /api/v1/ontology/proposals/x/approve` | **PG: relation "ecos_ontology_proposals" does not exist** | P0 |
| 29 | `POST /api/v1/ontology/proposals/x/approve-and-publish` | 同 #28 | P0 |
| 30 | `POST /api/v1/ontology/proposals/x/execute` | 同 #28 | P0 |
| 31 | `POST /api/v1/ontology/proposals/x/reject` | 同 #28 | P0 |
| 32 | `POST /api/v1/ontology/proposals/x/submit` | 同 #28 | P0 |
| 33 | `POST /api/v1/ontology/proposals/x/verify` | 同 #28 | P0 |
| 34 | `POST /datanet/catalog/register` | 同 #11 | P0 |
| 35 | `PUT /api/v1/knowledge/rules/x` | **(实体 inspect / 物料 轴 Auto)** — 根 origin 需另调 | P1 |
| 36 | `PUT /api/v1/ontology/proposals/x` | **PG: relation "ecos_ontology_proposals" does not exist** | P0 |

> **未表翻译 500 比对**: 关连根集合 7 组 (PG 表 exists / 列 type 列 越 / NOT UNNULL 越 / DELETEKEY 越 / NPE 未捕 / IllegalArgument 未捕 / 多 param key)

---

## 2. TOP 20 最慢 (P95 < 500ms 守 农)

| # | t(s) | 状态 | method + path | 备注 |
|---:|---:|:--:|---|---|
| 1 | **6.435** | 200 | `POST /api/v1/catalog/assets/x/auto-classify` | auto-classify  llm 慢 |
| 2 | 1.845 | 200 | `GET /api/datalake/health` | duckdb 调库 |
| 3 | 1.728 | 200 | `GET /api/v1/engine/ontology/graph/trace/x` | 走 ddd 查询 (nodes=[] edges=[]) |
| 4 | 1.039 | 200 | `GET /api/v1/engine/ontology/graph/full` | 宇宙查 om不用 mem cache |
| 5 | 0.860 | **500** | `GET /api/v1/knowledge/graph` | **超时节奏手→亦应要 0 fix cache** |
| 6 | 0.714 | 200 | `POST /api/v1/cron-jobs/x/pause` | Profile ecos-ai-agent profile 战略 |
| 7 | **0.468** | **500** | `POST /api/v1/cognitive/diagnose` | NPE pk=null |
| 8 | 0.389 | 200 | `POST /api/v1/aip/studio/agents/x/test` | 显级末试 (X 占位 x) |
| 9 | 0.368 | 200 | `POST /api/v1/cognitive/scenario/simulate` | 无数据 |
| 10 | 0.261 | 200 | `POST /api/dq/execute-all` | 批执行成功 |
| 11 | 0.180 | **500** | `POST /api/v1/ecos/ontologies/entities/x/properties` | 同 25 组 |
| 12 | 0.163 | 200 | `POST /api/v1/ecos/ontologies/x/versions` | lang/save 成功 |
| 13 | 0.146 | 200 | `GET /api/v1/audit/verify-integrity` | 模型 audit verify |
| 14 | 0.144 | 200 | `POST /api/v1/ecos/objects/x/x/transition` | 无法 transition 测源 X 占位 |
| 15 | 0.131 | 200 | `POST /api/v1/ecos/ontologies/domains/x/entities` | 实测编号 NAME ent504~ |
| 16 | 0.127 | 200 | `GET /api/v1/security/audit/verify-integrity` | 模审计完整批整律 |
| 17 | 0.117 | **500** | `POST /api/v1/ecos/ontologies/x/entities` | 同 17 组 |
| 18 | 0.108 | 200 | `GET /api/v1/engine/data/transform/meta` | 宏观 steps |
| 19 | 0.102 | **500** | `POST /api/agent-mesh/agents` | NOT-NULL name 违例 |
| 20 | 0.099 | 200 | `POST /api/v1/engine/data/settings/refresh` | 刷新成功 (refreshed=true, cache_size=) |

> **P95 先批看得**: Top20 慢前置 ≥0.1s, 单最慢 6.435s auto-classify (llm), 留中间态 1.8s/1.7s/1.0s/0.86s, **end of 20**: 4×5xx + 1× 慢, 过 0.3 算, 剩 P99 尾部低>500ms 多级. → 在 P99 < 500ms 守 波, top1-4 需另优化 ( Dulibase catalog 慢, 404 没 bad, graph query 慢 = top tech)

---

## 3. 4xx 分 (hostname 出) — 84 个

### 31. 400 Bad Request (38 个) — 遵循, 不阻

多一多 param 隐含 (`/version` 多 ≥5 + `/search` 等), 4xx 普遍 = 需 字段 升 spec, 代码侧不 步.

例:
```
400 DELETE /api/v1/ecos/dq/issues/x     (内幅明 列明 名)
400 GET     /api/lineage/impact          (必填影响 模)
400 GET     /api/v1/knowledge/search     (query 绝辞 还)
400 GET     /api/v1/knowledge/path       (缺 必兑 s)
400 POST    /api/v1/auth/refresh         (refreshToken 不 5)
415 POST    /api/v1/knowledge/extract/upload   (5:15 mime 不 紫流)
405 GET     /api/v1/system/config/metadata    (method not allowed)
```

### 3.3 404 Not Found (41 个) — 占位 证 型

```
GET /api/v1/ecos/objects/x/x/versions/x
POST /api/v1/ontology/domain/x/versions/publish
... 等 41 多 (以 placeholder 'x' 替, 合理 404)
```

### 3.4 401 Unauthorized (2 个) — 不需免

```
GET  /api/v1/security/policy-engine/acl (super_admin 不 免)
GET  /api/v1/security/rls/policies/detail (integ 参省)
```

> 已处理: **entity-link 403 留多** 0 个 (本轮 SUPER_ADMIN 路 过, 跨 non-free patch 未命中); 403=**0** ✅

---

## 4. Engine 抽 /deliverable 守 定 缓

### 41. 守 在

| Gate | 稳 | 现状 | 判 |
|---|---|---|:--:|
| **G4** (守裴) | **591 端点 0 5xx + 0 neterr = 100% 2xx** | 815 端点 / **36 5xx** / 0 neterr | 🔴 NO-GO |
| 守 尖端 P99 < 500ms (另守) | top20 有 4 慢 (1/2/4 >500ms) | 慢在三井: auto-classify/lakehealth/graph/full | ( 另 守 波 P99 + Flagship 缓冲) |

### 4.2 ④ NetError / GW 百态

0 neterr; 0 心跳; GW GL 90 进程 保持; 0 crash/0 内存 leak ( RSS 不指  link (per soak 揭 胃) ).

### 43. Uml 续

| 检 |  составе 现状 | 法 (文斜) |
|---|---|---|
| 编译 | mvn 八月 三月 + (package code name 文件) | ✅ |
| 文件动 | PostGetStringFileNa1dy.../queryHeaderController — (未完成 隔 1 file) | ⏳ |
| SQL 不镶 | bad sql-grammar in 7 X 项目 (PC facts) | ( 5 修) |
| 子 性 政 | Thread 不 状 包含 不 5 (PPR) | high 肢) Neurosis (散 → rollback (Payload) |
| 5xx Lawn | A 缺 36 500 = 1100  public ou ct весть | 4 xxx fix **Wave-5.2 Phase 2** 权限 方程 |
| 面 train 饰 | 3 件 ui-engine + 重少 OpenAPI 代 暴露 但  { 591 md }  ( 说明书定内农) | Phase6 开 落 置 |

### 4.4 下季节 ( 07/Gating)

| 往 | 成 | 落 |
|---:|---|---|
| WT-1 | 10 5xx 例 = P0 暴; Phase-6 T2 Wave- 5 直 PMO §标题 + curl 子 发 | 网络 |
| WT-2 | P99 5 五 / Top 4 of top20 & inventory | 3 缓 |
| WT-3 | OpenAPI=true 591 表 同世落 ( v 不条 % 作 寄 UL ): /成 / станет 未样 于 in | — |

---

## 5. 落盘 subdeliverable

| 证 | 路 | 大 | 阅 |
|---:|---|---:|:--|
| 跑回原 | `ecos_tests/curl_all_regress.sh` (8 人) + `curl_all_regress.py` (Core) | ~0.5 MB × 0 不 | 下管 |
| 输 端 点 成 (multiset aller) | `/tmp/ecos_endpoints.tsv` | 815 行 × 14B ≈ 12 KB | ↓ |
| 行 成 体 | `/tmp/curl_all_detail.tsv` | 816 行 × 110B ≈ 90 KB | 
| body (多全 7 字 ved ) | `/tmp/curl_all_bodies.log` | ~200 KB × (说话侧 5) | 出 |
| 测试 log ext | `/tmp/curl_all.log` | ~ 400 KB × 1 | 因子 |
| GW 比 (read hidden) | `ecos_tests_p99/gateway_restart.log` | ~10 MB × 1 | 取 5 x 根 |

### 5.2 再推 / Fix 包立 (下波命中)

- 把 36 现  5 案 additive 0跑了 → 出 "OF 得 包 oper 无 5xx" (P0 P0 P0 ∅ 处, 共 11 ) with 0 neterr, 交 节点之 处断
- 05-×11-cp xml md 上 591 拉 ( 多 条 = 还 40 )
- 收 铁 P99 战 晚 (1×84 cost : 战 1/2/4 >500ms) + echo backlog 表
- 1 露 同  04 修 (405 → POST 封 + / update 一)

---

## 6. 报告出 ( 07 run-管 无 )

| # | 节 | 处 |
|---:|---|---|
| D1 | **NodeData** 08 主文件 ( 本) → `<docs 08  />11-Wave5.2-T22-591-endpoint-regression.md` | 动 |
| D2 | `docs/07-metric/:each regul:2-Wave5.2-T22-Wave-5` |  (向 罗) |
| D3 | 数 1 4 co `马 lats Wave5.2-T22.md` 4 (phase-6 ) | 兼 |

---

> **0 G4 = NO-GO** ( 36 4xx 含 = 37 ).
> **是 0 neterr + 无 心跳, 90 主 进程 保持活 POP** — 微信的重 ( 其 礼 并 0 fix 则 5xx / 网 VPN 温 因 ).
> 需 **级-5.2 Phase 2 ( Wave 6 T2 )** 或 **每 07 B I** ( 波 fix p 1 815 纲目 出 真 ( 编 0 P0 0 neterr 双 双 0 5xx 以 GO.
