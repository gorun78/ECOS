-- V30: CEO场景 — Agent诊断工具注册 + 经营诊断Agent配置

-- ═══ 3个诊断工具 ═══
INSERT INTO ecos_tool_definition (id, code, name, description, tool_type, endpoint_url, http_method, schema_json, status) VALUES
('tool_deviation', 'query_worldmodel_deviation',
 '查询目标偏差',
 '读取WorldModel中所有目标的target_value vs current_value偏差，按偏差幅度排序',
 'API', '/api/dq/causal-deviation', 'GET',
 '{"params":[],"returns":"[{goalName, targetValue, currentValue, deviationPct, status}]"}',
 'ACTIVE'),
('tool_causal', 'trace_causal_chain',
 '追溯因果链',
 '沿因果链追溯根因：从偏差最大的目标开始，通过causal-link反向追溯直到找到根因节点',
 'API', '/api/v1/worldmodel/causal-graph', 'GET',
 '{"params":[],"returns":"{nodes:[],edges:[]}"}',
 'ACTIVE'),
('tool_scenario', 'generate_scenarios',
 '生成应对方案',
 '根据目标偏差和因果链自动生成应对场景方案（如更换供应商/谈判催货等）',
 'API', '/api/v1/worldmodel/scenarios', 'GET',
 '{"params":[],"returns":"[{id,name,description,configJson}]"}',
 'ACTIVE')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'ACTIVE';

-- ═══ 经营诊断Agent配置 ═══
INSERT INTO ecos_agent (id, name, model_provider, model_name, system_prompt, tools) VALUES
('agent_diagnostic', '经营诊断Agent',
 'deepseek', 'deepseek-v4-flash',
 '你是一个企业经营诊断专家。你的职责是：
1. 分析企业经营指标偏差（营收完成率62%、利润完成率58%、供应商准时率67%）
2. 沿因果链追溯根因（供应商→进度→营收→利润）
3. 生成具体的应对方案（更换供应商/谈判催货）

项目型企业领域知识：
- 产值率 = 实际产值/目标产值，当前62%
- 两金压降：应收账款+存货占营收比
- 回款率：实际回款/应回款，当前73% vs 目标85%
- 供应商风险：华强钢构准时率从92%降至67%，重点风险

当检测到任一目标实际值 < 目标值80%时，主动触发诊断分析。',
 '["query_worldmodel_deviation","trace_causal_chain","generate_scenarios"]')
ON CONFLICT (id) DO UPDATE SET
    system_prompt = EXCLUDED.system_prompt,
    tools = EXCLUDED.tools;

-- 同时注册到agent_registry
INSERT INTO ecos_agent_registry (id, name, role, capability, status, endpoint) VALUES
('agent_diagnostic', '经营诊断Agent', 'diagnostic',
 '{"tools":["query_worldmodel_deviation","trace_causal_chain","generate_scenarios"],"trigger":"goal_deviation > 20%","domain":"project_enterprise"}',
 'ACTIVE', '/api/v1/agent/chat')
ON CONFLICT (id) DO UPDATE SET
    capability = EXCLUDED.capability,
    status = 'ACTIVE';
