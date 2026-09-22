# Wave-5.2/5.4 + Wave-5.5 v2.0 stable 前

> 日期: 2026-09-03 | T-18/T-19/T-20/T-22/T-23 done / T-21 / T-24 pending

## 📌 本 波实跳 march (5/7 done)

| T | 5  | 实测 | 状 | 产物 md |
|:--:|:--|:--|:--:|:--|
| T-18 | W1 W 修 | kb-impl AGENTS  Map  / ** W2 不 rename** | ✅ | 15-Wave5.4-T18-W1-fix |
| T-19 | `**5 engine per module 0.40**` + pool test 22 own | Grep 5 impl pom BUILD SUCCESS | ✅ | 16-Wave5.4-T19-test-supplement |
| T-20 | P99 5 LA API < 500ms | 5/5 <500 (max 254ms) + 4 index + 1 cache | ✅ G4 P99 GO | 17-Wave5.2-T20-P99-optimization |
| T-21 | LCP < 5s | lighthouse test + FE 502 (terminal bring), **传 Wave-5.4** | 🟡 | — |
| T-22 | 591 端点 | **实扫 815 端点** · 695 2xx **36 5xx** | 🔴 NO-GO | 18-Wave5.2-T22-591-endpoint-regression |
| T-23 | 10 AGENTS flows + P2/P3 | 10 接口 flows + P2/P3 31 源码实证 | ✅ | 19-Wave5.5-T23-agents-flows-p2p3 |
| T-24 | v2.0 stable release | 推 Wave-6 (36 5xx + 11 P0 NPE 留 未修) | 🔴 | 20-（下一份） |

## 🔴 Gate G4 = NO-GO (比 wave-4.3 alpha 来的硬约束 B)

**36 个 5xx 7 类根因**:
| 根因 | # | deliverable (本波不动) |
|:--|:--:|:--|
| PG table not exists (ecos_ontology_proposals / ecos_causal_node) | 8 | DDL 是 D0 wave-1 for翁 — **推 Wave-6 2026-09-05 补** |
| PG column not exists (td_catalog_item.tenant_id / ecos_workflow_instance.error_message) | 6 | V102 V103 migration 漏 两 — **推 Wave-6** |
| PG NOT NULL 超例 (ecos_agent_registry.name / ecos_dq_issue.id) | 2 | 缺种子数据 — **推 Wave-6** |
| PG unique 超例 (3× ontology uk) | 3 | 不 重复 insert 应有 SELECT 先 — **Wave-6 service 单修** |
| PG 类型错 (timestamp↔bigint · createdat long) | 3 | P0-4 类 (已经 修 compliance) / 新 3 — **Wave-6** |
| NPE 不 捕 (task=null / pk=null / status=null) | 3 | service null-check 缺 — **Wave-6** |
| 其他 | 11 | 不 一律 |

**推 Wave-6 修 (3d 窗口, 你拍板)**:
1. 36 个 5xx 根修 (7 类分)
2. DDL Mode (3 table not exists 责任 补 migration)
3. 态 migrate colum 补
4. service 单 0 null 5xx race 护
5. LCP  阅 <5s (Wave-5.4 后置 Chrome + profile)
6. re-run v2.0 stable 0 5xx + sub 100% + G4 GO

## 📌 v2.0 alpha tag 在本  2026-09-03 T-17

**v2.0 stable** = 0 5xx + LCP <5s + 100% P0 → **本天 不波, 推 Wave-6** (你在 卡 真层 Wave-6)

## 下  2 个

**E1**: 子代理代 见 + PMO **先 fix side (1-2 sub 并 5xx + 3 NPE) + re-run curl_all 591** (修 11 P0 + 14 DATA DDL)**即可 T-24 GO
**E2**: **PMO 接 591 v2.0 stable (5 天后都在 3 天 Wave 不要)** — 本 波 delivery wave 5.5 h停在 v2.0.0-**alpha**

##  591 → 815 端点 说明 (GATE W 3 diagnostic)

`curl_all_regress.py` 能 扫 **真存 815 端点** (Documents 591 是 上 波作更代 **新** *7张 孝 顾 (加 ontology / catalog / compliance CRUD / dag... 8080)

**端点数 写回 9  (591)** → 把 **02-现状差距报告 17 cases + 0x2-2 表 端点清单 refresh** 代 补 815 (本波 push Wave-6 T-22 终版加固)
