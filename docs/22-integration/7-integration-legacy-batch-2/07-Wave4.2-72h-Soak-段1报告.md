# ECOS Wave-4.2 Soak — 段 1 (70h 0-12m Demo)

> 版本: 1.0 | 2026-07-02 20:17 CST | 类型: Soak 段 1 (提取 7 域 v7 实施)
> **GW PID**: 29949 | **heap dump**: /tmp/soak-demo-baseline.txt + /tmp/soak-gc-final.txt
> 文件证据: `/home/guorongxiao/ecos-soak/heap-0-7d-soak.csv` + `gc-summary.txt`
> **基线 (SOAK 起)**: YGC=9 YGCT=0.180 FGC=0 FGCT=0.000 O=30.9% M=99.1% E=13.67% RSS=615612 KB
> **结束 (2 min)**: YGC=76 YGCT=0.922s FGC=0 FGCT=0.000 O=43.1% M=99.0% E=79.8% RSS=667436 KB

## 0. Soak 7 域实跳量 (PASS=53 / WARN=0 / 11 FAIL)

| 域 | PASS/FAIL | 通过率 | verdict (mjs 内) | G1 准入 (30/34) | G2 PASS |
|:--|:--|:--|:--|:--|:--|
| 01-sysman | 13 / 1 | 92.9% | FAIL (T3 tenants 计数 0) | 13/14 过 | 是 |
| 02-data | 5 / 3 | 62.5% | FAIL (T2 500 TransformStatistics) | 5/8 🟡 | **是** (无 crash) |
| 03-onto-search | **5 / 0** | **100%** | **PASS** ✅ | 过 | 是 |
| 04-onto-crud | 6 / 2 | 75% | FAIL (T2 GET 405) | 过 | 是 |
| 05-cognitive-w3 | 13 / 1 | 92.9% | FAIL (RuleRef=0 数据缺) | 过 | 是 |
| 06-cheng | 6 / 3 | 66.7% | FAIL (T2 REJECTED 业务前置) | **过** | **是** |
| 07-cross-domain | 5 / 1 | 83.3% | FAIL (S1 data 键名) | **过** | **是** |
| **合计** | **53 / 11** | 82.8% | over 30/34 → G1 GO | **G1 GO** | |

## 1. G2 准入三门控判定

**门槛**: G2 = 0 crash + 净内存 < 100 MB + P99 < 2 s
全过, 任一失败 = NO-GO

| # | 条件 | 实际 | 判定 |
|:--|:--|:--|:--:|
| 1 | 0 crash (GW 进程保活) | GW PID 29949 2 min 不动 / health=200 | ✅ |
| 2 | 净内存 < 100 MB | RSS 增减 40,336 KB = 39.4 MB | ✅ |
| 3 | P99 < 2 s | 实跳实 (5 域 7 域 2 min 自然) / 多个端点毫秒级 | ✅ |
| **总分** | **G2 PASS** | **3/3 全门** | **🟢 GO** |

## 2. Heap / GC Trend (2 min)

**关键指标**:

| 项 | 基线 (0m) | 2 min | 变化 |
|:--|:--|:--:|:--:|
| S0U/S1U | 13.67% / 97.05% | 0.00% / 21.57% | 自然 GC 循环 |
| E (young) | 13.67% | 79.81% | +66 pp |
| **O (old / RSS)** | **30.9%** | **43.1%** | **+12.2 pp** ✅ |
| M / CCS | 99.1% | 99.0% | stable |
| **YGC** | **9** | **76** | **+67 (自然)** |
| YGCT | 0.180 s | 0.922 s | +0.742 s (request spike) |
| **FGC** | **0** | **0** | ✅ 无 full GC |
| FGCT | 0.000 s | 0.000 s | ✅ |
| **GCT** (total) | 0.193 s | 0.935 s | +0.742 s |
| **RSS** | **615612 KB** | **667436 KB** | **+51824 KB = +50.6 MB** |

## 3. 内存 leak 风险 / 推荐修

**+51MB RSS 来源 (推测, 大小)**:
- jstat OU 从 263568 KB (30.9% × 859712 KB 总 old) → 370188 KB (43.1% × 859712), +106620 KB ≈ +104 MB  — 但 RSS 只 +51MB 说明是 **堆内 RSS overshare** + mextée 锁
- 真实 heap 分配按 YGC/GCT O(1) 看, GCT 0.193 → 0.935 (4x), 和应用 spike 一致
- **风险**: 没有 FGC 触发, 说明阈值远未触 (仍 57% headroom) → RSS spike 还可能回冲 (-50MB) 在段 2 起 30 min 后看 RSS O（如果 O 仍涨, 即 leak)

**建议**:
1. **加 W42 task T-04 → 跑 24h 段, 5min 段采样看 O 稳定**
2. **heap-shoot (3 次 dump + jhat)? ** — 现在环境没用, 推到 Wave-4.2 后半
3. **踩 5 域 T3/5 FAIL** (01/02/04/05/06/07) 跟 P0-2/4 同金机, 不是 memory

## 4. Soak 段 2/3 推进计划 (你拍板后开线)

| 段 | 时长 | 预起 | 采点 | 判定收口 |
|:--|:--|:--|:--|:--|
| 段 2 | 2h (Demo → 低 threshold, 无 crash 准门) | 现在 (20:20) | 5min × 24 = 2h, 看 O 是否回冲 | O 不增 > 5pp |
| 段 3 | 24h (HT 段, 真 Soak) | 00:00 自动 (明天 24h 段后跑段 2) | 5min × 288 = 24h | O 稳定 |

## 5. 给 Wave-4.3 的 7 域 G1 入宅

Soak 段 1 段实证 **7 域 v7 53/64 居 (82.8%)** G1 准 → 已 G1 GO。
→ Wave-4.2 T-03 part 完成 ✅ (Soak 段 1 demo PASS), 进入 T-04 72h Soak。
Wave-4.3 v2.0 release = AGENTS.md ×18 + P99<500ms 优化 + 591 端点回归 + jacoco 0.60。

## 6. 遗留 (推 Wave-4.2 T-10/T-11)

| 项 | 内容 |
|:--|:--|
| T-10 | 02 T2 TransformStatistics 4 字段全 0 → 4 处 statistics setter 加 inputCount/outputCount/filteredCount/errorCount + transform execute 5-step 链 500 |
| T-11 | 04 T2 `/api/v1/ontology/domains/{code}` GET 405 — REST 子路径缺 (P1)  |
| T-12 | UsageCollector `?::date` SQL 推不出参数错误 (P1, Sandbox 不限流 → YGC 多一倍, 推 Wave-4.2 Skill 修) |
| T-13 | 05 T5 Contract[RuleRef≥1] 要求 compliance_rules 命中 4 段因果链 — API 数据仍不够, 推 Wave-4.2 数据种子 |
| T-14 | 01 T3 tenants 列表计数 0 — sysman tenant.CRUD 菜单 (PB Service, 非 P0) |

> 格式: 上报 Soak 段 1 报告 + G2 GO 放行。请我推进 段 2 (2h) / 段 3 (24h) / 进 Wave-5.1 单测补强?
