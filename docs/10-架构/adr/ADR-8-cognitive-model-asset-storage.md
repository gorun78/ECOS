# ADR-8 — 认知模型资产落盘载体（`ecos_cognitive_model`）

> 来源: 需求检视报告 §十二 Q11（表名事实源） | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0（v1.x 为铁律 §3.3 / current-plan.md 内联引用，本文件为权威载体）
> 裁决: **Accepted**
> 上游: 架构铁律 v2.0 §3.3（ADR-9 三档口径）+ 《数据库访问规范》v1.2 ST07/MC01/MC02
> 状态: 生效

---

## 1. 背景

铁律 §3.3 的三档落盘口径中，「模型**资产**」这一档指向 `ecos_cognitive_model`（V124 建），但从未定义：模型资产的边界（什么算模型）、版本化方式、与 K 层画像/假设的关系、schema 归属。PRD-05 又引入 `kb_cognitive_*` 误名（实测不存在），导致认知域表归属再度混乱。

## 2. 决策

1. **定义**：模型资产 = 可复用、可版本化的认知计算结构（因果图结构、效应估计参数、推理链路模板、能力掩码定义）。**不含**单次推理结论（不落盘，ADR-9）、**不含**业务事实与计算产物（业务域，ADR-14）。
2. **落点**：`ecos_cognitive.ecos_cognitive_model`（引擎控制域 schema，ST07 合规），主键 `VARCHAR(36)` 应用侧 UUID（MC01），必带 `domain` / `version_no` / 审计 5 字段（DR06/DR08）。
3. **版本化**：`version_no` 单调 + 逻辑删除（`is_deleted`），禁物理删除（R9）；每次发布产新版本行，旧版本保留并可被历史运行引用（REQ-FC-03 可复现的组成部分）。
4. **与 K 层的边界**：历史画像（分布/置信区间）与情景假设属**业务域 SEMANTIC/K 层知识资产**（湖规 v2.0 §一），由 kb 侧持有；`ecos_cognitive_model` 只持有**认知计算结构本身**并**以引用（定位符）方式**指向画像/假设版本，禁复制其数值（ST09 跨域只存定位符）。
5. **表名事实源（Q11）**：认知域现存权威表 = `ecos_cognitive_model` / `ecos_cognitive_evidence` / `ecos_cognitive_hypothesis` / `ecos_cognitive_belief`（+ `ecos_cognitive_run_invalidation`，需补入 ARCH-07 白名单否则 CI 误拦）。`kb_cognitive_hypothesis` / `kb_cognitive_belief` / `kb_mind_registry` 均为 PRD 误名，**禁止在任何新文档中继续使用**。
6. **Mind 注册载体**：Mind 定义属模型资产（能力掩码 + 闭环边界），落 `ecos_cognitive.ecos_cognitive_mind`；Mind↔场景绑定新建 `ecos_cognitive.ecos_cognitive_mind_binding`。**不复活** V146 `ecos_scenario_mind`（从未应用到 dev 库，且"scenario"前缀属场景侧实体，与 §0.6 编排定位冲突）。

## 3. 影响

| 维度 | 影响 |
|:--|:--|
| 一致性 | ✅ 认知域表名与 schema 归属单一事实源确立，PRD/代码/盘点三方对齐 |
| 数据设计 | 分册 05 的 E 章按本 ADR 出 DDL（含 `ecos_cognitive_mind` / `_mind_binding` 两张新表） |
| CI | ARCH-07 schema 白名单需补 `ecos_cognitive_run_invalidation`，否则新表被误判违规 |
| 存量 | `public.ecos_cognitive_*`（历史写入主控制 schema，78 表桶 B 的组成部分）按 ST07 附注 = knownLegacy，存量不迁；**新表一律 `ecos_cognitive.` 限定** |

## 4. 关联

ADR-9（三档口径）、ADR-14（计算产物落业务域）、《数据库访问规范》v1.2 §二附、`docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md` §三桶 B。

## 5. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.0 | 首次独立成文；定义模型资产边界、版本化、与 K 层引用规则、表名事实源与 Mind 载体 | AI Agent |
