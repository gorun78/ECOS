# 服务层 ming (K→W) 接口与验收 flows

> 明德·知识→智慧转化 | 宿主 ai-engine :18084 | 计划: services/ming-service (阶段 D4, 待建)

## 接入 flows
client → Gateway :8080 → security 闸门 (操作授权经 OPA evaluate) → `ai-engine` Agent/Loop/Tool 服务 (ming 转化实质) → 回 `ApiResponse` / SSE 流。
ming 无独立 module: 知识→智慧链路 = KB context (检索自 kb-engine) + Agent 推理 (ai-engine), LLM 调用统一走 `runtime/llm-gateway`, 不直连 provider。

## 主 API (curl)
```bash
curl -s -X POST "http://localhost:8080/api/v1/agent/chat" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"agentId":"cognitive-agent","message":"分析上周交付延迟的因果链"}'
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/run" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"agentId":"data-agent","goal":"清洗 sales 表"}'
curl -s "http://localhost:8080/api/v1/engine/ai/health" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
`ecos_agent` / `ecos_agent_session` (Agent+会话 V50/V101); LLM 调用日志 `agent_call_log` + profile 配置 `profile_config` (llm-gateway repository XML)。

## 别接 (调谁, 已核)
- 不直连 LLM provider (铁律 2.5 #2, 一律 `llm-gateway`)
- 不做规则判定 (cheng), 不做 KG 存储/RAG (kb-engine, RAG 检索 REST `GET :18086/api/v1/kb/rag`)
- 无新增基础设施 — 4 引擎 12 AGENTS P1 已用 IAgentRuntime 注入, 不 new

## 验收 flows
`POST /api/v1/agent/chat` SSE 流式返回 token 且最终 `status=COMPLETED`;
工具调用前 `POST /api/v1/security/policy-engine/evaluate` (ABAC) 裁决, 失败默认 DENY (security 不可用不降级)。发布事件: Agent 执行完成 → `PipelineEvent.of(STATUS_CHANGED, ...).fromModule("ming-service")`。
