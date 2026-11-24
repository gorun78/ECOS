-- ============================================================================
-- V364 — 心智模型承载编排（5 条内置，2026-11-24）
-- ============================================================================
-- 目标：为 V363 登记的 5 个心智模型各建一条真实可执行的 AIP 逻辑编排，
--       并把 ecos_mental_model_registry.pipeline_id 回填指向该编排。
--
-- 编排形态（画布契约 = definition_json.steps.graph.nodes/edges，见 AipPipelineExecutionService）：
--   input(用户问题) → llm(systemPrompt=心智模型提示词) → 输出 content
--   只用真实节点类型 llm；LLM 走 runtime/llm-gateway（本机 Qwen3.8-27B）。
--   不引入 approval/tool 节点——心智模型是纯推理，不需要人工门与外部工具。
--
-- 纪律：只加不删；status=active（可执行）；tenant_id 用 default（与既有编排一致）。
-- ============================================================================

-- ── 1. 第一性原理 ──────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_aip_pipeline
    (id, tenant_id, name, description, definition_json, status, template_ref, version_no, create_by, create_time, update_time)
SELECT
    'aip-mm-first-principles', 'tenant-a', '心智模型·第一性原理',
    '把问题拆到基本事实再重构方案（心智模型承载编排，V364）',
    '{"steps":{"blocks":[],"graph":{"nodes":[{"id":"n_input","type":"llm","position":{"x":100,"y":80},"data":{"type":"llm","label":"第一性原理推理","status":"idle","config":{"systemPrompt":"你是第一性原理分析助手。请把问题拆解到最基本的事实与约束，剔除所有未经证实的假设，再从这些基本事实出发重建可行方案。严格按以下四段输出，每段用标题开头：\\n【基本事实】\\n【被剔除的惯例】\\n【重构方案】\\n【关键风险】" ,"model":"Qwen3.8-27B","temperature":0.3,"maxTokens":2000,"writeChannel":false,"inputSlots":{}}}}],"edges":[]}}}',
    'active', 'mental-model/first-principles', '1', 'V364', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_aip_pipeline WHERE id = 'aip-mm-first-principles');

-- ── 2. 逆向思考 ────────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_aip_pipeline
    (id, tenant_id, name, description, definition_json, status, template_ref, version_no, create_by, create_time, update_time)
SELECT
    'aip-mm-invert', 'tenant-a', '心智模型·逆向思考',
    '先设想彻底失败再倒推必做事项（心智模型承载编排，V364）',
    '{"steps":{"blocks":[],"graph":{"nodes":[{"id":"n_input","type":"llm","position":{"x":100,"y":80},"data":{"type":"llm","label":"逆向思考推理","status":"idle","config":{"systemPrompt":"你是逆向思考分析助手。请先设想该问题彻底失败的场景，列出导致失败的关键原因，再倒推为避免失败必须避免什么、必须做到什么。严格按以下四段输出，每段用标题开头：\\n【失败路径】\\n【根因】\\n【反向行动项】\\n【早期预警信号】" ,"model":"Qwen3.8-27B","temperature":0.3,"maxTokens":2000,"writeChannel":false,"inputSlots":{}}}}],"edges":[]}}}',
    'active', 'mental-model/invert', '1', 'V364', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_aip_pipeline WHERE id = 'aip-mm-invert');

-- ── 3. 概率思维 ────────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_aip_pipeline
    (id, tenant_id, name, description, definition_json, status, template_ref, version_no, create_by, create_time, update_time)
SELECT
    'aip-mm-probabilistic', 'tenant-a', '心智模型·概率思维',
    '用概率分布表达判断并关注尾部风险（心智模型承载编排，V364）',
    '{"steps":{"blocks":[],"graph":{"nodes":[{"id":"n_input","type":"llm","position":{"x":100,"y":80},"data":{"type":"llm","label":"概率思维推理","status":"idle","config":{"systemPrompt":"你是概率思维分析助手。请对问题中的关键变量给出概率区间估计与依据，计算期望结果，并指出尾部风险与信息缺口。严格按以下四段输出，每段用标题开头：\\n【关键变量及概率】\\n【期望值】\\n【尾部风险】\\n【需要补充的信息】" ,"model":"Qwen3.8-27B","temperature":0.3,"maxTokens":2000,"writeChannel":false,"inputSlots":{}}}}],"edges":[]}}}',
    'active', 'mental-model/probabilistic', '1', 'V364', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_aip_pipeline WHERE id = 'aip-mm-probabilistic');

-- ── 4. 复利 ────────────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_aip_pipeline
    (id, tenant_id, name, description, definition_json, status, template_ref, version_no, create_by, create_time, update_time)
SELECT
    'aip-mm-compound', 'tenant-a', '心智模型·复利',
    '关注长期累积效应与临界点（心智模型承载编排，V364）',
    '{"steps":{"blocks":[],"graph":{"nodes":[{"id":"n_input","type":"llm","position":{"x":100,"y":80},"data":{"type":"llm","label":"复利思维推理","status":"idle","config":{"systemPrompt":"你是复利思维分析助手。请识别问题中可积累的要素，估算其随时间累积的效应与临界点，比较短期收益与长期复利。严格按以下四段输出，每段用标题开头：\\n【可积累要素】\\n【累积效应测算】\\n【临界点】\\n【长期行动建议】" ,"model":"Qwen3.8-27B","temperature":0.3,"maxTokens":2000,"writeChannel":false,"inputSlots":{}}}}],"edges":[]}}}',
    'active', 'mental-model/compound', '1', 'V364', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_aip_pipeline WHERE id = 'aip-mm-compound');

-- ── 5. 激励机制 ────────────────────────────────────────────────────────────
INSERT INTO ecos_ai.ecos_aip_pipeline
    (id, tenant_id, name, description, definition_json, status, template_ref, version_no, create_by, create_time, update_time)
SELECT
    'aip-mm-incentive', 'tenant-a', '心智模型·激励机制',
    '从各方激励结构解释行为并设计可行激励（心智模型承载编排，V364）',
    '{"steps":{"blocks":[],"graph":{"nodes":[{"id":"n_input","type":"llm","position":{"x":100,"y":80},"data":{"type":"llm","label":"激励机制推理","status":"idle","config":{"systemPrompt":"你是激励机制分析助手。请分析问题涉及各方的激励结构，解释当前行为背后的利益动因，识别激励错配，并给出可行的激励调整方案。严格按以下四段输出，每段用标题开头：\\n【各方激励结构】\\n【行为解释】\\n【错配点】\\n【调整方案】" ,"model":"Qwen3.8-27B","temperature":0.3,"maxTokens":2000,"writeChannel":false,"inputSlots":{}}}}],"edges":[]}}}',
    'active', 'mental-model/incentive', '1', 'V364', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_ai.ecos_aip_pipeline WHERE id = 'aip-mm-incentive');

-- ── 6. 回填心智模型目录的承载编排 ────────────────────────────────────────
UPDATE ecos_control.ecos_mental_model_registry SET pipeline_id = 'aip-mm-first-principles', update_by = 'V364', update_time = NOW()
 WHERE code = 'FIRST_PRINCIPLES' AND (pipeline_id IS NULL OR pipeline_id = '');
UPDATE ecos_control.ecos_mental_model_registry SET pipeline_id = 'aip-mm-invert', update_by = 'V364', update_time = NOW()
 WHERE code = 'INVERT' AND (pipeline_id IS NULL OR pipeline_id = '');
UPDATE ecos_control.ecos_mental_model_registry SET pipeline_id = 'aip-mm-probabilistic', update_by = 'V364', update_time = NOW()
 WHERE code = 'PROBABILISTIC' AND (pipeline_id IS NULL OR pipeline_id = '');
UPDATE ecos_control.ecos_mental_model_registry SET pipeline_id = 'aip-mm-compound', update_by = 'V364', update_time = NOW()
 WHERE code = 'COMPOUND' AND (pipeline_id IS NULL OR pipeline_id = '');
UPDATE ecos_control.ecos_mental_model_registry SET pipeline_id = 'aip-mm-incentive', update_by = 'V364', update_time = NOW()
 WHERE code = 'INCENTIVE' AND (pipeline_id IS NULL OR pipeline_id = '');
