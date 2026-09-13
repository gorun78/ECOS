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
| /api/v1/world-model/* | * | 世界模型 |

## 我的数据库表
- 复用 kb-engine 的合规规则表（compliance_rules）
- 推理**结果**不持久化，实时计算
- **模型资产**注册表 `ecos_cognitive_model`（ADR-8 口径修订，PMO-50：推理结果不落盘，模型资产可落盘）

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| kb-engine | GET :18086/api/v1/kb/rules | 规则查询 |
| kb-engine | POST :18086/api/v1/kb/graph/query | KG推理 |

## 禁止
1. 不直接import kb-engine的impl模块
2. 不新增数据库表（ADR-8 口径修订：推理**结果**不落盘，实时计算；模型**资产**注册落 `ecos_cognitive_model`，见 PMO-50 决策 1）
3. 不引入规则引擎（SpEL表达式评估即可）
