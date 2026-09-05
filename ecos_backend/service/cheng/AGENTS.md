# 服务层 cheng (K→C) 接口与验收 flows

> 诚意·知识→认知转化 | 宿主 cognitive-engine :18089 | 计划: services/cheng-service (阶段 D4, 待建)

## 接入 flows
client → Gateway :8080 → security 闸门 → `cognitive-engine` 推理/决策服务 (cheng 转化实质) → 回 `ApiResponse`。
cheng 无独立 module: 认知接口落在 cognitive-engine-impl (`/api/v1/cognitive/*`), 推理只读 `kb-engine` `GET /api/v1/kb/rules` (REST, 不 import kb-impl), 不直接读 kb 表。

## 主 API (curl)
```bash
curl -s -X POST "http://localhost:8080/api/v1/cognitive/diagnose" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"subject":"supplier-001","metric":"ontimeRate"}'
curl -s -X POST "http://localhost:8080/api/v1/cognitive/reason" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"caseId":"case-001"}'
curl -s "http://localhost:8080/api/v1/engine/cognitive/health" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
cognitive 不新增 DB 表 (铁律 3.3), 推理实时计算; 只读复用 `compliance_rules` (kb 写 cognitive 读, 经 REST) + `ecos_decision_case` (决策快照)。

## 别接 (调谁, 已核)
- 不执行规则 CRUD (那是 kb-engine 的事), 不写规则/知识存储
- 不引入规则引擎 (SpEL 即可, `SpelConditionEvaluator`), 不直接调 LLM (W 层走 ai-engine / `llm-gateway`)
- 不直接 import kb-engine-impl (铁律 2.1) — 跨引擎只走 REST

## 验收 flows
`POST /api/v1/cognitive/diagnose` 返回因果链 ≥3 层 (chain length 断言);
Neo4j 因果边查询 (enterprise 档) 超时 ≤10s 且结果 ≤1000 节点。发布事件: 决策落盘 → `PipelineEvent.of(STATUS_CHANGED, ...).fromModule("cheng-service")`, 审计异步写 security-engine。
