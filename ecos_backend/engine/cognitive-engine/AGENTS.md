# cognitive-engine — 认知推理引擎

> 端口: **18089** | PMO: **ecos-pm** | 依赖: kb-engine-api

## 我负责的
- **KnowledgeReasonerService**: 混合检索引擎（KG_QUERY / RULE_CHECK / VECTOR_RAG / HYBRID）
- **RuleCausalService**: 合规因果链推理
- **RuleImpactService**: 规则变更影响分析
- **CausalReasoner**: 业务因果链推理（复用）
- **ScenarioSimulator**: 场景模拟（复用）
- **ForecastService**: 统计基线预测（移动均值/线性外推 + KG 因子修正，PMO-51）

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/knowledge/reason | POST | 混合推理 |
| /api/v1/rules/causal-chain/{ruleId} | GET | 合规因果链 |
| /api/v1/rules/impact-analysis | POST | 规则变更影响分析 |
| /api/v1/rules/audit-logs | GET | 合规审计日志 |
| /api/v1/cognitive/forecast | POST | 指标预测（PMO-51） |
| /api/v1/cognitive/models | GET/POST | 认知模型注册表（PMO-51） |
| /api/v1/cognitive/* | * | 认知推理通用端点 |
| /api/v1/cognitive/evidence | GET/POST | 心智层证据 列表/登记（PMO-59 P2a，ADR-9） |
| /api/v1/cognitive/evidence/{id} | GET | 心智层证据详情（PMO-59 P2a） |
| /api/v1/cognitive/hypotheses | GET/POST | 心智层假设 列表/注册（PMO-59 P2a） |
| /api/v1/cognitive/hypotheses/{id} | GET | 心智层假设详情（PMO-59 P2a） |
| /api/v1/cognitive/hypotheses/{id}/invalidate | POST | 假设人工失效（P2a；自动失效检测 P2b） |
| /api/v1/cognitive/beliefs | GET/POST | 不确定性判断 列表（domain 必填）/注册（prob 和=1 强校验，PMO-59 P2a） |
| /api/v1/cognitive/beliefs/{id} | GET | 不确定性判断详情（PMO-59 P2a） |
| /api/v1/world-model/* | * | 世界模型 |

> 心智层契约（PMO-59 P0）：`cognitive-engine-api` 已登记 `IUncertaintyJudgementService` / `IHypothesisLifecycleService` 两接口（仅契约无实现，端点留待 Phase 2 开放，届时按上表模式登记 `/api/v1/cognitive/...` 具体路径）。
> **P2a 端点已开放（2026-09-14，ADR-9 正式落盘）**：上表 7 行心智层端点即 Phase 2 第一单交付物；`beliefs/{variable}/update-by-evidence`（贝叶斯更新）与 `beliefs/{variable}/override`（人工覆写）P2b 落（api-contract §3.4 已预登记）。

## 我的数据库表（ADR-9 三档落盘口径，PMO-59 P0）
- 复用 kb-engine 的合规规则表（compliance_rules，只读）
- **① 推理"结果"不落盘**：推理实时计算，不持久化（ADR-8 原口径保留）
- **② 模型"资产"落盘**：`ecos_cognitive_model`（模型注册/版本/回测，ADR-8 修订，PMO-51）
- **③ 认知"心智状态"落盘**（ADR-9，PMO-59 P0 解锁，DDL V127~V129 手工执行）：
  - `ecos_cognitive_evidence` — 证据/来源/可信度 0~1/冲突标记（感知层输出物）
  - `ecos_cognitive_hypothesis` — 假设/证据引用/valid/失效时间（失效检测 Phase 2）
  - `ecos_cognitive_belief` — 不确定性判断（原稿"信念"统一改称）：变量名/有限离散概率分布 JSONB/版本/`snapshot_version`（时间回放 Phase 3 预留）/人工覆写标记

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| kb-engine | GET :18086/api/v1/kb/rules | 规则查询 |
| kb-engine | POST :18086/api/v1/kb/graph/query | KG推理 |

## 禁止
1. 不直接import kb-engine的impl模块
2. 不违反 ADR-9 三档落盘口径新增 DB 表：推理**结果**不落盘（实时计算）；仅模型**资产**（`ecos_cognitive_model`）与认知**心智状态**（`ecos_cognitive_evidence` / `ecos_cognitive_hypothesis` / `ecos_cognitive_belief`）可落盘（ADR-9，PMO-59）
3. 不引入规则引擎（SpEL表达式评估即可）
