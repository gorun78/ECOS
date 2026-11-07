-- ============================================================
-- V316__agent_registry_knowledge_carrier.sql
-- B35：COGNITION 边（KNOWLEDGE_BASE→AI_AGENT）契约载体落位
-- ============================================================
-- 背景（实证）：
--   workspace 场景完整度的 COGNITION 必需边在契约探测面直接抛
--   「COGNITION 边无契约载体：智能体注册表未持有知识库引用（需求侧缺口，非探测故障）」——
--   智能体注册表 ecos_ai.ecos_agent_registry 只有 metadata JSONB，其中无任何知识库引用键。
--
-- 载体选择（不新增表/列）：
--   metadata JSONB 本就是该表的扩展位（现承载 domain/tier/tools/bind_* 等）。
--   本次在**场景实际绑定的认知智能体** `ag_ent2_cognitive` 上补 knowledgeBases 数组，
--   元素取知识库**业务键**（与 ecos_scenario_binding_link 里
--   sb_ent2_gm_kb = KNOWLEDGE_BASE 'ent2_doc_profit_caliber' 同键），不引入第二套编码。
--   workspace 契约探测改为读该键计数（HttpContractProbeFacade#probeCognition）。
--
-- 语义边界（不越界）：
--   本脚本只表达「该智能体声明消费哪些知识库」这一条配置事实，不含任何业务结果；
--   幂等：仅当 metadata 尚无 knowledgeBases 键时写入，已有值一律不覆盖（重复执行无副作用）。
--
-- 回滚说明：metadata 为 JSONB 单键新增，回滚 = 逐行
--   `UPDATE ... SET metadata = metadata - 'knowledgeBases' WHERE ...`（IR03 不主动执行）。
-- ============================================================

UPDATE ecos_ai.ecos_agent_registry
   SET metadata = jsonb_set(metadata, '{knowledgeBases}', '["ent2_doc_profit_caliber"]'::jsonb, true),
       updated_at = NOW()
 WHERE id = 'ag_ent2_cognitive'
   AND NOT (metadata ? 'knowledgeBases');

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER TYPE；仅 JSONB 单键写入 ✓
-- [DR01] schema 限定 ecos_ai ✓  [ST07] AI 认知域表 ✓
-- [DR04] 未新增列；JSONB 既有形态不改 ✓
