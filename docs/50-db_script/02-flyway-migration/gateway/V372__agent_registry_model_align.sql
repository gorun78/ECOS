-- ============================================================================
-- V372 — 旧内置 Agent 元数据模型名对齐（2026-11-26 用户授权「模型改为 V4.1」）
-- ============================================================================
-- 背景（活体取证）：`POST /api/v1/aip/agents/builtin-ai-agent/invoke` 经 AgentLoop 发起时
--   使用 `deepseek-chat`，内网 OpenAI 兼容网关（llm.gateway.base-url）返回
--   `503 model_not_found: No available channel for model deepseek-chat`。
--   该名来自旧 builtin-* 行的 metadata.model（2026-11-10 历史种子），而仓库现行
--   默认模型由 `sys_config llm 组 llm.default-model` 决定（当前为 DeepSeek-V4.1-Flash-1017，
--   上游 `GET /v1/models` 与真实 chat 调用均已实测通过；`deepseek-v4.1-1017` 一名不被上游提供）。
--
-- 处置：只 UPDATE 六枚旧 builtin-* 行的 metadata.model / metadata.modelName 两个键，
--   不动 systemPrompt / toolWhitelist / guardrails / 状态（INACTIVE 保持 INACTIVE）。
--   活跃 Copilot 六智能体（agent-*）由 DataInitializer 种子按 llm.default-model 刷新，无需本脚本。
--
-- 纪律：IR03 只加不删（纯 UPDATE 数据，零 DDL）；MC03 不用 :: cast / ON CONFLICT / RETURNING /
--       gen_random_uuid / JSONB 运算符（取值与设值走 jsonb_set + to_jsonb，类型以标准 CAST 显式化，
--       `to_jsonb('x')` 会因 unknown 多态报错，故必须 CAST 到 text）；
--       ST07 归属 ecos_ai（AI 域注册表）；幂等：重复执行结果一致。
-- ============================================================================

UPDATE ecos_ai.ecos_agent_registry
   SET metadata = jsonb_set(
                      jsonb_set(metadata, '{model}',
                                to_jsonb(CAST('DeepSeek-V4.1-Flash-1017' AS text))),
                      '{modelName}',
                      to_jsonb(CAST('DeepSeek-V4.1-Flash-1017' AS text))),
       updated_at = NOW()
 WHERE id IN ('builtin-ai-agent', 'builtin-data-agent', 'builtin-ontology-agent',
              'builtin-cognitive-agent', 'builtin-security-agent', 'builtin-kb-agent')
   AND metadata IS NOT NULL;
