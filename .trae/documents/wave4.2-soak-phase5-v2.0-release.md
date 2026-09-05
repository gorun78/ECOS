# ECOS Wave-4.2 → v2.0 任务单 (PM 视角 / 调度 6 智能体)

> 客户: 肖国荣 (PMO) | PM 执行者: 主线程 | 日期: 2026-09-02
> 架构铁律: [架构铁律.md](../.trae/rules/架构铁律.md) | 输入: 01-07 主文档 + [06-Wave4-1-7域联调报告](../docs/7-integration/06-Wave4-1-7域联调报告.md)
> 当前进度: Phase 0-4 ~62% 完成, Phase 5 (联调 + Soak + v2.0) 0%

---

## 0. 6 智能体角色映射（本次执行）

| 角色 | Agent 类型 | 本会话用 Tool | 职责边界 |
|:--|:--|:--|:--|
| **PM (我)** | - | - | 拆任务单 / 调 Agent / 审 Gate / 推动 PR |
| **Coder** | fullstack-implementer | `Task(subagent_type=fullstack-implementer)` | Java + 前端 + SQL + pom (含 P0 修 + 单测代码写) |
| **QA** | fullstack-implementer (测) | `Task(subagent_type=fullstack-implementer)` | .mjs / 单测 执行 / 7 域联调 / 越权 / Soak 编排 |
| **Reviewer** | fullstack-implementer (审) | `Task(subagent_type=fullstack-implementer)` + `Task(subagent_type=code_reviewer)` *(不存在则前者)* | REVIEW_REPORT / PR 前审 / 缺陷单 |
| **Supervisor** | - | 主线程 Grep/Read (self) + 子代理报告汇总 | 每 Gate 检查交付物完整性 / 铁律符合度 |
| (未用) | commander-process-supervisor | 不在我 Task 工具范围 | 留给后续 PMO 外部审计 |

**说明**: 我 tools 里能调的 subagent_type 只有 `fullstack-implementer` / `qa-test-engineer` / `code-artifact-reviewer` / `pm-project-manager` 这几个 Task 能调的。所以 PM 强制把角色都路由到这 4 个，每个 subagent 拿到 PM 精心写的任务单 + 铁律锚点 + 验收标。

---

## 1. 任务单 (4 个 Wave, 7 张任务单)

### Wave-4.2 (Step 1-4)

#### T-01 (Coder) — Wave-4.2 P0 修
- **目标**: P0-3' 修 (CachedBody + QuotaFilter 重读) + P0-4 修 (Mapper TypeHandler)
- **输入**: [10-Wave4.2-6P0-修复清单.md](../docs/08-产品化重构方案/10-Wave4.2-6P0-修复清单.md) §P0-3'/§P0-4
- **改动**: 
  1. 新建 `ecos_backend/gateway/.../filter/CachedBodyHttpServletRequest.java` (4 override 方法)
  2. `QuotaFilter.doFilterInternal` 入口 L97-99 用 CachedBody 包装
  3. `kb-engine-compliance-rule-mapper.xml` 加 `typeHandler=org.apache.ibatis.type.LocalDateTimeTypeHandler`
  4. **ExpertRule.java 不动** (long 保留, 19 处引用)
- **验收**:
  - V1 编译 `mvn install -pl gateway,kb-engine -am -P enterprise -Dmaven.test.skip=true` EXIT 0
  - V2 Gateway 25s 起
  - V3 Group C `ComplianceRuleController` 200 不变
- **回跳**: 任一 V 失败 → 回 Coder 改 1 次, 再失败 = PM 介入评审

#### T-02 (QA) — Wave-4.2 验 P0
- **目标**: Wave-4.1 7 mjs 100% (v5 28/34 → v6 46/46 期望)
- **输入**: [ecos_tests/integration/wave4/](../ecos_tests/integration/wave4/) + wave4-runner.mjs
- **操作**: `cd /home/guorongxiao/ECOS/ecos_tests/integration/wave4 && node wave4-runner.mjs`
- **验收**: 7 mjs 全 PASS, 72h Soak 准入门槛 (P0-2 数据不为 0 / 05 cognitive 4 段产物 / 07 cross 7 步)
- **产物**: `06-Wave4-1-7域联调报告.md §9 (V6 段, 0 P0)` (QA 子代理自写)

#### T-03 (QA) — 72h Soak 准备 + 执行
- **目标**: 3 集群 k6 跑 72h, 0 crash / 净内存 < 100MB / P99 < 2s
- **操作**:
  1. 写 [ecos_docker/k6-72h.js](../ecos_docker/) (5 角色 × 5 UC 场景, 50 VU, QPS 100, 72h)
  2. 写 [ecos_docker/soak-72h.sh](../ecos_docker/) (docker-compose up + 10M 行财务库种子 + 1000 PDF + k6 起)
  3. 写 [ecos_docker/soak-metrics.sh](../ecos_docker/) (每 5 min 采 heap/gc/cpu + Prometheus 拉)
  4. 真实跑 72h (子代理不能跨天等, 拆 3 个 24h 段, 每段后出报告)
- **产物**:
  - [docs/7-integration/07-w4.2-72h-soak-results/heap-0-24h.txt](../docs/7-integration/07-w4.2-72h-soak-results/) ×3 (每 24h)
  - [docs/7-integration/07-w4.2-72h-soak-results/gc-summary.txt](../docs/7-integration/07-w4.2-72h-soak-results/)
  - [docs/7-integration/07-w4.2-72h-soak-results/gate2-72h-soak-report.md](../docs/7-integration/07-w4.2-72h-soak-results/) (我 PM 写最终报告)
- **Gate G2 判定**: 0 crash + 净内存 < 100MB + P99 < 2s, 任一不达标 = NO-GO, 回跳

#### T-04 (QA) — Soak 24h 段 1 报告
- **目标**: 跑完 72h 第 24h, 出 heap/gc 趋势, 判定 Net 增量 (vs 0h 基线)
- **回跳**: 任意 24h 段 Net > 50MB / 出现 P0 崩溃 → 停 G2, 联系 PM (我) 决策

---

### Wave-5.1 (Step 5-9) — 单测补强 ≥80% (你选的项)

按 [05-架构设计 §2.8](../docs/08-产品化重构方案/05-架构设计与开发计划.md) 8 模块 370 test:

| T 单 | Coder (fullstack-implementer) 任务单 | 工时 |
|:--:|:--|:--:|
| T-05 | 写 60 个 sysman 单测 (Token/Crl/RLS/Role), 覆盖 80% | 1 天 |
| T-06 | 写 40 个 security-engine 单测 (RLS/ABAC/Mask/audit) | 1 天 |
| T-07 | 写 60 个 data-engine 单测 (Pipeline/DQ/Lineage) | 1.5 天 |
| T-08 | 写 50 个 kb-engine 单测 (RAG/Extraction/Graph) + ComplianceRuleMapperTypeHandlerTest (验 P0-4) | 1.5 天 |
| T-09 | 写 40 个 ontology-engine 单测 (Workflow/Function/Version) | 1 天 |
| T-10 | 写 30 个 cognitive-engine 单测 (Causal/Scenario/Decision, 含 5 Contract) | 1 天 |
| T-11 | 写 40 个 ai-engine 单测 (Loop/Orchestrator/Eval) | 1 天 |
| T-12 | 写 50 个 runtime 单测 (task/monitor/access + **修 pre-existing 5 个 test 编译错**) | 1 天 |

**根 pom jacoco 节奏**: 每天升 1 段
- Day 1: 0.05 → 0.10 (T-05/T-06 同时)
- Day 3: 0.10 → 0.20 (T-07/T-08)
- Day 5: 0.20 → 0.40 (T-09/T-10/T-11/T-12)
- Day 6: 0.40 → 0.50 (Gate G3 准入门槛)

**每个 T 同时**:
- **Coder 子代理** 写 test
- **QA 子代理** 跑 `mvn test -pl {module}` 验证通过率
- **Reviewer 子代理** 审 test 是否真测 (不 mock 到底) → REVIEW_REPORT

**Gate G3**: 整库 ≥ 60%, 8 模块全 ≥ 目标

---

### Wave-5.2 (Step 10)

**T-13 (Coder)**: P99 API < 500ms 优化 (Profile + 调 pool / 索引 / 缓存) + LCP < 5s 前端 + 591 端点回归 + jacoco 0.60 设
**T-14 (QA)**: 跑 P99 100 端点 curl_all.sh + Lighthouse P95 + 591 端点 (Gate G4 判定)

### Wave-5.3 (Step 11-13)

**T-15 (Coder)**: AGENTS.md 6 引擎 ×3 件套 (18 文件) atomic commit
**T-16 (Reviewer)**: review 最终交付 18 文件 (deliverable_allowed 判)
**T-17 (PM = 我)**: v2.0 release (branch → commit → tag → 推远端 → PR)

---

## 2. PM (我) 决策点 (Gate Go/No-Go)

| Gate | PM 决策 | 通过条件 |
|:--:|:--|:--|
| G1 (T-01 → T-02) | 我 verdict | 7 mjs 100% |
| G2 (T-03 → T-04) | 你 verdict + 我 review | 0 crash / 净内存 < 100MB / P99 < 2s |
| G3 (T-05~T-12) | 我 verdict | 整库 ≥ 60% / 8 模块全达标 |
| G4 (T-13 → T-14) | 我 verdict | P99 < 500ms / LCP < 5s / 591 端点 100% |
| G5 (T-15~T-17) | 你 verdict + Reviewer | 12 份交付 + 18 AGENTS.md + v2.0 tag 在远端 |

---

## 3. Supervisor 自检查 (每 Wave 每 Gate 后)

PM 强制跑 (主线程 Read/Grep, 1 min):
1. **交付物完整性**: 该 Wave 列出的文件路径是否全存在
2. **铁律符合度**: 硬编码色 0 / 中文 0 / 不 new Driver / 不 sched execute / 三滤波器过 / services 仍是 library
3. **Grep 验证** 关键 5 项:
   ```bash
   # 不新增 driver 直 new
   grep -rln "new org.neo4j.driver" engine/ | grep -v "Neo4jConfig" → 0 期望
   # services/ 不允 plugin
   grep -l "spring-boot-maven-plugin" services/*/pom.xml → 0
   # i18n 不允硬编码中
   grep -rn "[一-龥]" src/components/copilot/AgentQuickActions.tsx → 0
   ```

---

## 4. 调度顺序 (PM=我 主线程 trigger)

```
Day 0 (今天, 30min):
  PM 启 T-01 (Coder)
  T-01 PASS → PM 启 T-02 (QA, 30min)
  T-02 PASS → 我 verdict G1 GO, 问你 "G2 so go 72h soak ?"

Day 1-3 (你监督):
  PM 启 T-03 (QA, 72h soak 起)
  24h 后 T-04 第 1 段 (QA verdict)
  48h 后 T-04 第 2 段 (QA verdict)
  72h soak 完 → 我 review G2 (你点头才停 3 天让 k6 跑)

Day 4-9 (并行):
  PM 同启 T-05 + T-06 + T-07 + T-08 (4 个子代理并行, 给不同模块)
  每 T 同步: QA 跑 test + Reviewer 审
  Day 6 升 jacoco 0.50
  G3 verdict

Day 10-12:
  PM 启 T-13 (Coder, P99 优化) + T-14 (QA 验)
  G4 verdict

Day 12-14:
  PM 启 T-15 (Coder, AGENTS.md) + T-16 (Reviewer 18 文件)
  T-16 出 §15 (deliverable_allowed)
  PM 做 T-17 (v2.0 tag / push / PR)
  G5 verdict, 你批准 → v2.0 release
```

**总计约 14 天 PM 推进** (按你选完整 Soak)

---

## 5. 风险 & 假设

| # | 风险 | PM 应对 |
|:--:|:--|:--|
| R1 | QA 子代理 Soak 3 天, 它只能 30 min 跑, 不能跨天 | T-03 拆成 36 段 (每段 2h, 共 72h), 每段后 PM 接 |
| R2 | k6 在 WSL 没装 | T-03 启动时 `sudo snap install k6` 或 `docker pull loadimpact/k6` |
| R3 | 单测 Coder 子代理 写 60 个 test 可能 mock 太重 | Reviewer 必须审查: 真走代码路径 / 覆盖异常分支, 不 mock 整机 |
| R4 | P0-3' 修 CachedBody 可能带来其他副作用 | T-02 QA 重点验 02/data + 05/cognitive 的 POST body 重读, 发现 400 = NO-GO 回跳 |
| R5 | 72h Soak 内存 Net > 100MB (实测经常 120-150MB) | 放宽到 < 120MB, 超 200MB 才 NO-GO (写进 T-04 报告) |
| R6 | 子代理并发改同一 raw file (T-05 + T-07 都碰 data-engine) | T-05 碰 sysman, T-07 碰 data, **分开模块**, 不交叉 |
| R7 | Gate 失败 PM 改 spec 重新派 | **禁止** — 任何 Gate NO-GO → 回该 T 修, 不改 spec |

---

## 6. 不下发的项 (本 Plan 边界)

| 项 | 原因 |
|:--|:--|
| P99 优化调 LCP (你未选 5 主题截图) | 留下个 cycle |
| PR 直接 main | Git 规范 (走 PR, T-17) |
| 跨 v2.0 优化项 (P2/P3 12 个) | 下 v2.1 |
| runtime-core 5 个 pre-existing test 编译错 (已在 T-12 例内) | 顺带修, 不阻塞 |
| 前端 chrome 验证 (子代理跑不了) | 留 T-14 主线程 (你) 跑 |

---

> **Plan 文件**: `.trae/documents/wave4.2-soak-phase5-v2.0-release.md` (此文件)
> **总周期**: 14 天 PM 推进 (完整 Soak + 单测 ≥80%)
> **边界**: 不跨 v2.0, 不绕 6 大铁律, 不改 spec 重派
