# 服务层 zhi (I→K) 接口与验收 flows

> 致知·信息→知识转化 | 宿主 kb-engine :18086 (wave-card 归属: 本体/知识抽取+规则物化) | 计划: services/zhi-service (阶段 D4, 待建)

## 接入 flows
client → Gateway :8080 → security 闸门 → `kb-engine` 的抽取/规则/实体链接服务 (zhi 转化实质) → 回 `ApiResponse`。
zhi 无独立 module: 本体→KG 同步走 `POST /api/v1/kb/graph/sync` (GraphSyncController), 知识抽取走 `POST /api/v1/knowledge/extract`, 规则物化走 `/api/v1/kb/rules` 写 `compliance_rules`。

## 主 API (curl)
```bash
curl -s -X POST "http://localhost:8080/api/v1/kb/graph/sync" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"entityType":"Department","versionId":"v1"}'
curl -s -X POST "http://localhost:8080/api/v1/knowledge/extract" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"articleId":"art-001"}'
curl -s "http://localhost:8080/api/v1/kb/rules" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
`compliance_rules` (规则+版本, cognitive 只读复用); `kb_graph_node` / `kb_graph_edge` (graph 物化, enterprise 档 Neo4j 双写)。

## 别接 (调谁, 已核)
- 不执行规则判定 (那是 cognitive-engine `POST /api/v1/cognitive/diagnose` 的事)
- 不直接调 LLM (抽取经 kb-engine 走 `llm-gateway`, 不自行调 provider)
- Neo4j 写入走 `runtime-access` Neo4jClient Bean; 存量 `KGWriterService` import `org.neo4j.driver.*` 是波 5 迁 runtime 的已知债

## 验收 flows
`POST /api/v1/kb/graph/sync` 后 `GET /api/v1/kb/graph/query` (cypher `MATCH (n) RETURN count(n)`) 计数增加;
`POST /api/v1/knowledge/extract` 后断言返回 subGraph 非空。发布事件: zhi 完成 → `PipelineEvent.of(TRANSFORM_COMPLETED, ...).fromModule("zhi-service")`。
