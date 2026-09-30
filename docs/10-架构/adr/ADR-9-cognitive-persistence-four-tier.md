# ADR-9 — 认知落盘三档口径（推理结果 / 模型资产 / 心智状态）

> 来源: 需求检视报告 §十二 Q3/Q11 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v2.0（v1.x 内联于铁律 §3.3 与 current-plan.md；本文件为权威载体，v2.0 加入确定性计算产物的第四档澄清）
> 裁决: **Accepted**
> 上游: 架构铁律 v2.0 §3.3 / §0.6 / §3.5 + 《数据库访问规范》v1.2 §〇/ST07~ST09 + ADR-8 / ADR-14
> 状态: 生效，全工程强制

---

## 1. 背景

铁律 §3.3 规定 cognitive-engine 的落盘只有三档，并在 v1.9 起被引用为"cognitive 不新增 DB 表"的取代条款。需求检视暴露两个问题：

1. 该口径未说明**业务域计算产物**（预测运行的金额/区间/回测指标）是否被"推理结果不落盘"禁止——PRD-05 与 PRD-09 因此给出互斥解读；
2. 三档中的表名在 PRD 侧被写成 `kb_cognitive_*` 等不存在的名字（Q11）。

## 2. 决策（四档口径）

| 档 | 内容 | 落盘 | 载体 |
|:--|:--|:--|:--|
| ① 推理**结果** | 单次推理/因果解释/诊断结论的即时输出 | **不落盘**（实时计算，按需重放） | 响应体 + 日志（traceId 可回放） |
| ② 模型**资产** | 因果图结构、效应估计参数、推理链路模板、能力掩码 | 落盘 | `ecos_cognitive.ecos_cognitive_model`（ADR-8） |
| ③ 心智**状态** | 假设 / 信念 / 证据 | 落盘 | `ecos_cognitive_{hypothesis,belief,evidence}`（V127~129 已建） |
| ④ **确定性计算产物**（v2.0 新增澄清） | 预测运行的金额、区间、瀑布、回测指标 | 落盘，**但属业务域** | `APPLICATION` 层：`ecos_dw.fc_forecast_result` 等；schema 与写通道归 **data-engine**，cognitive 经 REST 提交（ADR-14） |

**关键澄清（v2.0）**：
- 「推理结果不落盘」**只约束 ①**，即认知的即时结论；它**不是**"cognitive 相关的数据都不许存"。
- ④ 之所以可存，是因为它是**业务域应用层数据资产**（湖规 v2.0 §一：`APPLICATION` 生产者=聚合管道、消费者=场景工作台），而非认知控制数据；因此**不破**"cognitive 引擎零新增控制域表"的约束——cognitive 自己仍只有 ②③ 两类表。
- ②③ 属控制域（引擎 schema），④ 属业务域（`ecos_dw`）；两者之间**只存定位符**（ST09）：认知侧记录 `forecastRunId` + `inputSnapshotRef`，不复制金额明细。

## 3. 禁令（Reviewer 逐条判定）

1. cognitive 不得在 `ecos_cognitive` / 主控制 schema 内新建 ④ 类表（业务数据入控制域 = ST08 FAIL）；
2. cognitive 不得直查 `ecos_dw.*` 或 data-engine 的表（跨引擎只走 REST，§3.3 + ArchUnit C2）；
3. ③ 三表与 ② 之外的 cognitive 新表 = 违规（本 ADR 取代原"不新增 DB 表"，但**未放开**第四类控制表）；
4. 推理结果若需可回放，走日志 + traceId（REQ-NF-04），不得为"方便回放"落盘；
5. 文档/代码中禁止再出现 `kb_cognitive_hypothesis` / `kb_cognitive_belief` / `kb_mind_registry` 三个误名（Q11）。

## 4. 影响

| 维度 | 影响 |
|:--|:--|
| 需求 | REQ-COG-* 与 REQ-FC-* 的落盘歧义消解；PRD-05/09 需求基线按 §2 表格改写（任务 #26） |
| 实现 | 现网 cognitive 侧存在 `public.ecos_cognitive_*`（历史写主控制 schema，78 表桶 B）与 5 张 `ecos_cognitive_*`——knownLegacy 不迁，新表按引擎 schema 限定 |
| 违规存量 | `DiagnosticAgentService.java:80,101,122`（ai-engine SQL 直查 cognitive `ecos_wm_*`）同时违反本 ADR §3-2 与 ST09，改造项 C2 |
| CI | `ecos_cognitive_run_invalidation` 需入 ARCH-07 白名单；新表 DDL 评审加"档别"一栏（①②③④） |

## 5. 关联

ADR-8（模型资产载体）、ADR-14（计算主体与写通道）、ADR-3（单库 5+1）、《数据湖存储分层规范》v2.0 §一/§七A。

## 6. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v2.0 | 首次独立成文；三档 → 四档（补 ④ 确定性计算产物）；表名事实源入宪（Q11） | AI Agent（用户裁决 Q3/Q11） |
