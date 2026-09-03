# llm-gateway (器·LLM 统一网关) 接口与验收 flows

> 横切底座·器 | cloudless (随 gateway fat-JAR) | 全仓 LLM 调用唯一出口
> 源码: LLMGatewayService / LLMGatewayImpl / ProfileManager / SessionManager / AgentScheduler / AgentMetrics / CallbackExecutor + AgentCallLog / ProfileConfig Repository

## 接入 flows
client (ai-engine Agent/Loop/任意引擎工具) → 注入 `LLMGatewayService` Bean → `execute/executeAsync/executeWithSession` → `LLMGateway` 调 provider + 记录 `AgentCallLog` → 回 `AgentResult`。
铁律 2.5 #2: 所有 LLM/推理调用统一走此处, 各引擎禁止直接调 OpenAI/DeepSeek provider (环境变量 DEEPSEEK_API_KEY 仅 gateway 持有)。

## 主 API (curl)
llm-gateway 无 own REST (其调用方 = ai-engine Agent 端点), 用 ai-engine 入口作冒烟:
```bash
curl -s -X POST "http://localhost:8080/api/v1/agent/chat" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"agentId":"kb-agent","message":"检索'交付延迟'相关知识"}'
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/run" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"agentId":"data-agent","goal":"清洗并映射 sales"}'
curl -s "http://localhost:8080/api/v1/engine/ai/health" -H "Authorization: Bearer $TOKEN"   # 健康链包含 llm-gateway profile 可加载
```

## 接 DB 表
`agent_call_log` (每次调用: 模型/token/耗时/成本, MyBatis XML `AgentCallLogRepository.xml`); `profile_config` (profile → 模型/温度/系统提示映射, `ProfileConfigRepository.xml`)。

## 别接 (调谁, 已核)
- agent/profile/session 业务逻辑不在此 (在 ai-engine) — 这里是 provider + 计量 + 会话基础设施
- 不存业务上下文 (上下文来自调用方), 凭据不硬编码 (`LLMGatewayProperties`)
- 回调 `CallbackExecutor` 只发进程内事件, 不外调 provider

## 验收 flows
ai-engine chat 流式返回 token 且 `agent_call_log` 插入 1 行 (status=SUCCESS, token count > 0);
无 `DEEPSEEK_API_KEY` 时 execute 抛业务异常 (401/424 视 provider), 不降级为 mock — 验收用 `agent_call_log` 查最近记录断言 provider 字段与 profile 配置一致。
